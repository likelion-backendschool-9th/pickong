package com.pickong.feed.application.port.out;

import com.pickong.common.domain.Visibility;
import com.pickong.feed.domain.model.Feed;
import java.util.List;
import java.util.Set;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface FeedRepository {
    Feed save(Feed feed);

    // 아래 목록 조회는 모두 cursor 이후를 최신순으로 limit개 (서비스가 hasNext 판별용으로 limit+1을 넘김)

    /** 추천 탭: 다른 사람의 PUBLIC 게시물. viewerId의 글은 빼고, null이면 PUBLIC 전체 */
    List<Feed> findRecommendedFeeds(Long viewerId, Long cursor, int limit);

    /** 팔로잉 탭: viewerId의 글 전부 + followeeIds의 PUBLIC, FOLLOWERS 게시물 */
    List<Feed> findFollowingFeeds(Long viewerId, List<Long> followeeIds, Long cursor, int limit);

    Feed findById(Long feedId);

    Feed findByIdForUpdate(Long feedId);

    Feed findByPublicIdForUpdate(UUID publicId);

    boolean updateHiddenAt(long feedId, OffsetDateTime previous, OffsetDateTime hiddenAt, OffsetDateTime now);

    void deleteById(Long feedId);

    // 사진 중 하나라도 다른 게시물(exceptFeedId 제외, 새 게시물이면 null)에 이미 연결됐는지
    boolean existsPhotoInOtherFeed(java.util.Collection<Long> mediaFileIds, Long exceptFeedId);

    // 좋아요 수 증가 · 감소 (동시 요청 안전하게 DB에서 직접 연산)
    void incrementLikeCount(Long feedId);
    void decrementLikeCount(Long feedId);

    // 북마크 수 증가 · 감소
    void incrementBookmarkCount(Long feedId);
    void decrementBookmarkCount(Long feedId);

    /** 프로필 피드: ownerUserId의 게시물 중 공개 범위가 visibilities에 드는 것 */
    List<Feed> findByOwner(Long ownerUserId, Set<Visibility> visibilities, Long cursor, int limit);
}
