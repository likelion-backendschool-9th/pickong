package com.pickong.feed.application.port.out;

import com.pickong.feed.domain.model.Feed;
import java.util.List;

public interface FeedRepository {
    Feed save(Feed feed);

    List<Feed> findLatestFeeds(int limit);

    List<Feed> findRecommendedFeeds(Long userId, int limit);

    List<Feed> findFollowingFeeds(Long userId, int limit);

    List<Feed> findFeedsByTag(Long userId, List<String> tags, int limit);

    Feed findById(Long feedId);

    void deleteById(Long feedId);
}
