package com.pickong.feed.application.port.out;

import com.pickong.feed.domain.model.Feed;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface FeedRepository {
    Feed save(Feed feed);

    // cursor 이후의 피드를 limit+1개 조회 (hasNext 판별용)
    List<Feed> findRecommendedFeeds(Long userId, Long cursor, int limit);

    List<Feed> findFollowingFeeds(Long userId, Long cursor, int limit);

    Feed findById(Long feedId);

    Feed findByIdForUpdate(Long feedId);

    Feed findByPublicIdForUpdate(UUID publicId);

    boolean updateHiddenAt(long feedId, OffsetDateTime previous, OffsetDateTime hiddenAt, OffsetDateTime now);

    void deleteById(Long feedId);

    // 좋아요 수 증가 · 감소 (동시 요청 안전하게 DB에서 직접 연산)
    void incrementLikeCount(Long feedId);
    void decrementLikeCount(Long feedId);

    // 북마크 수 증가 · 감소
    void incrementBookmarkCount(Long feedId);
    void decrementBookmarkCount(Long feedId);

    // 특정 사용자의 피드 목록 (isOwner=true면 모든 visibility, false면 PUBLIC만)
    List<Feed> findByOwner(Long ownerUserId, boolean isOwner, Long cursor, int limit);
}
