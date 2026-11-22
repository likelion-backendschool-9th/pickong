package com.pickong.feed.application.port.out;

import com.pickong.feed.domain.model.Feed;
import java.util.List;

public interface FeedRepository {
    Feed save(Feed feed);

    // cursor 이후의 피드를 limit+1개 조회 (hasNext 판별용)
    List<Feed> findRecommendedFeeds(Long userId, Long cursor, int limit);

    List<Feed> findFollowingFeeds(Long userId, Long cursor, int limit);

    Feed findById(Long feedId);

    void deleteById(Long feedId);
}
