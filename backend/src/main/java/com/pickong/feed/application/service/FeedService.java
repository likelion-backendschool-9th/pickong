package com.pickong.feed.application.service;

import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.FeedPage;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.application.port.out.FeedRepository;
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
    public Feed getFeed(Long feedId) {
        return feedRepository.findById(feedId);
    }

    @Override
    public FeedPage getRecommendedFeeds(Long userId, Long cursor, int limit) {
        // limit+1개 조회해서 hasNext 판별
        List<Feed> feeds = feedRepository.findRecommendedFeeds(userId, cursor, limit + 1);
        return toPage(feeds, limit);
    }

    @Override
    public FeedPage getFollowingFeeds(Long userId, Long cursor, int limit) {
        List<Feed> feeds = feedRepository.findFollowingFeeds(userId, cursor, limit + 1);
        return toPage(feeds, limit);
    }

    @Override
    @Transactional
    public Feed createFeed(CreateFeedCommand command) {
        Feed feed = Feed.create(command.getUserId(), command.getContent(), command.getVisibility(),
                command.getMediaFileIds(),
                command.getPlaceIds());
        return feedRepository.save(feed);
    }

    @Override
    @Transactional
    public Feed updateFeed(Long feedId, UpdateFeedCommand command) {
        Feed feed = feedRepository.findById(feedId);
        feed.update(command.getContent(), command.getVisibility(), command.getMediaFileIds(), command.getPlaceIds());
        return feedRepository.save(feed);
    }

    @Override
    @Transactional
    public void deleteFeed(Long feedId) {
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
