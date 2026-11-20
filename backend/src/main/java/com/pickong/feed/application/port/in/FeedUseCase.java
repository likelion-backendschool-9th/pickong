package com.pickong.feed.application.port.in;

import com.pickong.feed.domain.Feed;
import java.util.List;

public interface FeedUseCase {
    List<Feed> getLatestFeeds(int limit);

    List<Feed> getRecommendedFeeds(Long userId, int limit);

    List<Feed> getFollowingFeeds(Long userId, int limit);

    List<Feed> getFeedsByTag(Long userId, List<String> tags, int limit);

    Feed createFeed(Long placeId, Long authorId, String content, List<String> imageUrls);

    Feed updateFeed(Long feedId, Long placeId, String content, List<String> imageUrls);

    void deleteFeed(Long feedId);
}
