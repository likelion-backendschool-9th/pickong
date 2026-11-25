package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.FeedPage;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.domain.model.Feed;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedService implements FeedUseCase {
    private final FeedRepository feedRepository;

    public FeedService(FeedRepository feedRepository) {
        this.feedRepository = feedRepository;
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
        feed.ensureReadable(viewerId, admin);
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
        Feed feed = feedRepository.findByIdForUpdate(feedId);
        // 본인 피드만 삭제 가능
        if (!feed.getUserId().equals(userId)) {
            throw new BusinessException(FeedErrorCode.FEED_NOT_OWNER);
        }
        feedRepository.deleteById(feedId);
    }

    // limit+1개 조회 결과로 FeedPage 생성 (hasNext, nextCursor 계산)
    private FeedPage toPage(List<Feed> feeds, int limit) {
        boolean hasNext = feeds.size() > limit;
        List<Feed> result = hasNext ? feeds.subList(0, limit) : feeds;
        Long nextCursor = hasNext ? result.get(result.size() - 1).getId() : null;
        return new FeedPage(result, nextCursor, hasNext);
    }
}
