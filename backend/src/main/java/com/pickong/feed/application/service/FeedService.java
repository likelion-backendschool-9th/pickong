package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.FeedPage;
import com.pickong.feed.application.dto.FeedPlaceGroup;
import com.pickong.feed.application.dto.FeedPlaceSummary;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.domain.model.Feed;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedService implements FeedUseCase {
    private final FeedRepository feedRepository;
    private final UserPersistencePort userPersistencePort;

    public FeedService(FeedRepository feedRepository, UserPersistencePort userPersistencePort) {
        this.feedRepository = feedRepository;
        this.userPersistencePort = userPersistencePort;
    }

    @Override
    @Transactional(readOnly = true)
    public Feed getFeed(Long feedId) {
        return feedRepository.findById(feedId);
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
        Feed feed = feedRepository.findById(feedId);
        // 본인 피드만 수정 가능
        if (!feed.getUserId().equals(userId)) {
            throw new BusinessException(FeedErrorCode.FEED_NOT_OWNER);
        }
        feed.update(command.getContent(), command.getVisibility(), command.getMediaFileIds(), command.getPlaceIds());
        return feedRepository.save(feed);
    }

    @Override
    @Transactional
    public void deleteFeed(Long feedId, Long userId) {
        Feed feed = feedRepository.findById(feedId);
        // 본인 피드만 삭제 가능
        if (!feed.getUserId().equals(userId)) {
            throw new BusinessException(FeedErrorCode.FEED_NOT_OWNER);
        }
        feedRepository.deleteById(feedId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedPlaceGroup> getFeedPlaces(String tab, Long userId) {
        // tab 값 검증: recommended·following 외 값은 400 반환
        if (!tab.equals("recommended") && !tab.equals("following")) {
            throw new BusinessException(FeedErrorCode.INVALID_TAB);
        }

        // 탭별로 피드-장소 조인 행 목록 조회
        List<FeedPlaceSummary> rows = tab.equals("following")
                ? feedRepository.findFollowingFeedPlaces(userId)
                : feedRepository.findRecommendedFeedPlaces();

        // placeId 기준으로 그룹핑 → 장소별 피드 ID 목록 생성
        Map<Long, List<FeedPlaceSummary>> grouped = rows.stream()
                .collect(Collectors.groupingBy(FeedPlaceSummary::getPlaceId));

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
        // username으로 사용자 조회 — 없으면 404
        Long ownerUserId = userPersistencePort.findByUsername(username)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .getId();

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
