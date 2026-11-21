package com.pickong.feed.application.port.in;

import com.pickong.feed.domain.model.Feed;
import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import java.util.List;

public interface FeedUseCase {

    List<Feed> getLatestFeeds(int limit);

    List<Feed> getRecommendedFeeds(Long userId, int limit);

    List<Feed> getFollowingFeeds(Long userId, int limit);

    List<Feed> getFeedsByTag(Long userId, List<String> tags, int limit);

    Feed createFeed(CreateFeedCommand command);

    Feed updateFeed(Long feedId, UpdateFeedCommand command);

    Feed getFeed(Long feedId);

    void deleteFeed(Long feedId);
}
