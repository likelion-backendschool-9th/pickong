package com.pickong.feed.application.port.in;

import com.pickong.common.domain.Visibility;
import com.pickong.feed.domain.model.Feed;
import java.util.List;

public interface FeedUseCase {
    List<Feed> getLatestFeeds(int limit);

    List<Feed> getRecommendedFeeds(Long userId, int limit);

    List<Feed> getFollowingFeeds(Long userId, int limit);

    List<Feed> getFeedsByTag(Long userId, List<String> tags, int limit);

    Feed createFeed(Long userId, String content, Visibility visibility, List<Long> mediaFileIds, List<Long> placeIds);

    Feed updateFeed(Long feedId, String content, Visibility visibility, List<Long> mediaFileIds, List<Long> placeIds);

    void deleteFeed(Long feedId);
}
