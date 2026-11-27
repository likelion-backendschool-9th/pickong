package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.FeedPage;
import com.pickong.feed.application.dto.FeedPlaceGroup;
import com.pickong.feed.application.dto.FeedPlaceSummary;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.application.port.out.FeedPlaceQueryPort;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.domain.model.Feed;
import com.pickong.social.application.port.in.RelationQueryUseCase;
import com.pickong.user.application.port.in.UserQueryUseCase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedService implements FeedUseCase {
    private final FeedRepository feedRepository;
    private final FeedPlaceQueryPort feedPlaceQueryPort;
    private final UserQueryUseCase userQueryUseCase;
    private final RelationQueryUseCase relationQueryUseCase;

    public FeedService(FeedRepository feedRepository,
                       FeedPlaceQueryPort feedPlaceQueryPort,
                       UserQueryUseCase userQueryUseCase,
                       RelationQueryUseCase relationQueryUseCase) {
        this.feedRepository = feedRepository;
        this.feedPlaceQueryPort = feedPlaceQueryPort;
        this.userQueryUseCase = userQueryUseCase;
        this.relationQueryUseCase = relationQueryUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public Feed getFeed(Long feedId) {
        return getFeed(feedId, null, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Feed getFeed(Long feedId, Long viewerId, boolean admin) {
        Feed feed = feedRepository.findById(feedId);
        // 관리자면 관계를 계산하지 않고 통과, 아니면 보는 사람과 작성자의 관계로 공개 범위 판정
        feed.ensureReadable(admin ? null : relationQueryUseCase.relationOf(viewerId, feed.getUserId()), admin);
        return feed;
    }

    @Override
    @Transactional(readOnly = true)
    public FeedPage getRecommendedFeeds(Long userId, Long cursor, int limit) {
        // limit+1개 조회해서 hasNext 판별
        List<Feed> feeds = feedRepository.findRecommendedFeeds(userId, cursor, limit + 1);
        return toPage(feeds, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedPage getFollowingFeeds(Long userId, Long cursor, int limit) {
        List<Feed> feeds = feedRepository.findFollowingFeeds(userId, cursor, limit + 1);
        return toPage(feeds, limit);
    }

    @Override
    @Transactional
    public Feed createFeed(CreateFeedCommand command) {
        // userId는 Controller에서 @CurrentUser로 꺼내 command에 담아 넘어옴
        Feed feed = Feed.create(command.getUserId(), command.getContent(), command.getVisibility(),
                command.getMediaFileIds(),
                command.getPlaceIds());
        return feedRepository.save(feed);
    }

    @Override
    @Transactional
    public Feed updateFeed(Long feedId, Long userId, UpdateFeedCommand command) {
        Feed feed = feedRepository.findByIdForUpdate(feedId);
        // 1. 볼 수 있는 글인지 → 2. 본인 글인지. 1번이 없으면 남의 비공개 글에 403이 나가서 글이 있다는 게 드러남
        // 소프트 삭제된 글은 행이 남아 있지만 1번에서 404로 막힘 (수정해서 되살릴 수 없음)
        feed.ensureReadable(relationQueryUseCase.relationOf(userId, feed.getUserId()), false);
        if (!feed.getUserId().equals(userId)) {
            throw new BusinessException(FeedErrorCode.FEED_NOT_OWNER);
        }
        feed.update(command.getContent(), command.getVisibility(), command.getMediaFileIds(), command.getPlaceIds());
        return feedRepository.save(feed);
    }

    @Override
    @Transactional
    public void deleteFeed(Long feedId, Long userId) {
        // 1. 행 잠금 → 2. 볼 수 있는 글인지 (이미 삭제됨, 남의 숨김 글, 공개 범위 밖이면 모두 404)
        // → 3. 본인 글인지 → 4. deleted_at만 채움. 숨김 글도 작성자는 볼 수 있어서 삭제할 수 있음
        Feed feed = feedRepository.findByIdForUpdate(feedId);
        feed.ensureReadable(relationQueryUseCase.relationOf(userId, feed.getUserId()), false);
        if (!feed.getUserId().equals(userId)) {
            throw new BusinessException(FeedErrorCode.FEED_NOT_OWNER);
        }
        // 행을 잠그고 읽어서 동시에 두 번 삭제해도 여기는 한 번만 true. 그래도 false면 이미 삭제된 것으로 봄
        if (!feedRepository.softDelete(feedId)) {
            throw new BusinessException(FeedErrorCode.FEED_NOT_FOUND);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedPlaceGroup> getFeedPlaces(String tab, Long userId) {
        // tab 값 검증: recommended·following 외 값은 400 반환
        if (!tab.equals("recommended") && !tab.equals("following")) {
            throw new BusinessException(FeedErrorCode.INVALID_TAB);
        }

        // 비로그인(userId=null) + following 탭: 팔로우 목록이 없으므로 쿼리 없이 빈 목록 반환
        if (tab.equals("following") && userId == null) {
            return List.of();
        }

        // 탭별로 피드-장소 조인 행 목록 조회 (크로스모듈 쿼리 → FeedPlaceQueryPort 경유)
        List<FeedPlaceSummary> rows = tab.equals("following")
                ? feedPlaceQueryPort.findFollowingFeedPlaces(userId)
                : feedPlaceQueryPort.findRecommendedFeedPlaces();

        // placeId 기준으로 그룹핑 → 장소별 피드 ID 목록 생성 (LinkedHashMap으로 최신 피드 순서 유지)
        Map<Long, List<FeedPlaceSummary>> grouped = rows.stream()
                .collect(Collectors.groupingBy(FeedPlaceSummary::getPlaceId, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<FeedPlaceSummary> group = entry.getValue();
                    List<Long> feedIds = group.stream()
                            .map(FeedPlaceSummary::getFeedId)
                            .distinct()
                            .toList();
                    return new FeedPlaceGroup(group.getFirst(), feedIds);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FeedPage getUserFeeds(String username, Long viewerUserId, Long cursor, int limit) {
        // username으로 사용자 id 조회 — 없으면 UserQueryUseCase가 404 던짐
        Long ownerUserId = userQueryUseCase.findIdByUsername(username);

        // 본인이면 모든 visibility, 타인이면 PUBLIC만
        boolean isOwner = ownerUserId.equals(viewerUserId);
        List<Feed> feeds = feedRepository.findByOwner(ownerUserId, isOwner, cursor, limit + 1);
        return toPage(feeds, limit);
    }

    // limit+1개 조회 결과로 FeedPage 생성 (hasNext, nextCursor 계산)
    private FeedPage toPage(List<Feed> feeds, int limit) {
        boolean hasNext = feeds.size() > limit;
        List<Feed> result = hasNext ? feeds.subList(0, limit) : feeds;
        Long nextCursor = hasNext ? result.get(result.size() - 1).getId() : null;
        return new FeedPage(result, nextCursor, hasNext);
    }
}
