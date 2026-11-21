package com.pickong.feed.application.service;

import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import java.util.List;

import org.springframework.stereotype.Service;

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
    public Feed createFeed(CreateFeedCommand command) {
        Feed feed = Feed.create(command.getUserId(), command.getContent(), command.getVisibility(),
                command.getMediaFileIds(),
                command.getPlaceIds());
        return feedRepository.save(feed);
    }

    @Override
    public Feed updateFeed(Long feedId, UpdateFeedCommand command) {
        Feed feed = feedRepository.findById(feedId);
        feed.update(command.getContent(), command.getVisibility(), command.getMediaFileIds(), command.getPlaceIds());
        return feedRepository.save(feed);
    }

    @Override
    public void deleteFeed(Long feedId) {
        feedRepository.deleteById(feedId);
    }
}
