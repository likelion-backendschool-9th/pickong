package com.pickong.feed.application.service;

import com.pickong.common.domain.Visibility;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import java.util.List;

public class FeedService implements FeedUseCase {
    private final FeedRepository feedRepository;

    public FeedService(FeedRepository feedRepository) {
        this.feedRepository = feedRepository;
    }

    @Override
    public List<Feed> getLatestFeeds(int limit) {
        return feedRepository.findLatestFeeds(limit);
    }

    @Override
    public List<Feed> getRecommendedFeeds(Long userId, int limit) {
        return feedRepository.findRecommendedFeeds(userId, limit);
    }

    @Override
    public List<Feed> getFollowingFeeds(Long userId, int limit) {
        return feedRepository.findFollowingFeeds(userId, limit);
    }

    @Override
    public List<Feed> getFeedsByTag(Long userId, List<String> tags, int limit) {
        return feedRepository.findFeedsByTag(userId, tags, limit);
    }

    @Override
    public Feed createFeed(Long userId, String content, Visibility visibility,
                           List<Long> mediaFileIds, List<Long> placeIds) {
        Feed feed = Feed.create(userId, content, visibility, mediaFileIds, placeIds);
        return feedRepository.save(feed);
    }

    @Override
    public Feed updateFeed(Long feedId, String content, Visibility visibility,
                           List<Long> mediaFileIds, List<Long> placeIds) {
        Feed feed = feedRepository.findById(feedId);
        feed.update(content, visibility, mediaFileIds, placeIds);
        return feedRepository.save(feed);
    }

    @Override
    public void deleteFeed(Long feedId) {
        feedRepository.deleteById(feedId);
    }
}
