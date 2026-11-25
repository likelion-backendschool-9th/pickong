package com.pickong.feed.application.port.in;

import com.pickong.feed.domain.model.Feed;
import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.dto.FeedPage;
import java.util.List;

public interface FeedUseCase {

    // cursor: 마지막으로 조회한 feedId (첫 요청 시 null)
    FeedPage getRecommendedFeeds(Long userId, Long cursor, int limit);

    FeedPage getFollowingFeeds(Long userId, Long cursor, int limit);

    Feed createFeed(CreateFeedCommand command);

    Feed updateFeed(Long feedId, Long userId, UpdateFeedCommand command);

    Feed getFeed(Long feedId);

    Feed getFeed(Long feedId, Long viewerId, boolean admin);

    void deleteFeed(Long feedId, Long userId);
}
