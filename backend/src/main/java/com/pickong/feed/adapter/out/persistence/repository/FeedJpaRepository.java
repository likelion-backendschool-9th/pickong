package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.FeedJpaEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedJpaRepository extends JpaRepository<FeedJpaEntity, Long> {
    List<FeedJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // PUBLIC 피드를 최신순으로 조회 (cursor 없으면 첫 페이지)
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.visibility = 'PUBLIC' " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findRecommendedFeeds(
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 팔로잉한 사용자의 PUBLIC 피드를 최신순으로 조회
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.userId IN (SELECT fw.followeeId FROM FollowJpaEntity fw WHERE fw.followerId = :userId) " +
           "AND f.visibility = 'PUBLIC' " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findFollowingFeeds(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // like_count를 DB에서 직접 +1 — 동시 요청에서도 카운트 정확하게 유지됨
    @Modifying
    @Query("UPDATE FeedJpaEntity f SET f.likeCount = f.likeCount + 1 WHERE f.id = :feedId")
    void incrementLikeCount(@Param("feedId") Long feedId);

    // like_count -1, 단 0 미만으로 내려가지 않도록 조건 추가
    @Modifying
    @Query("UPDATE FeedJpaEntity f SET f.likeCount = f.likeCount - 1 WHERE f.id = :feedId AND f.likeCount > 0")
    void decrementLikeCount(@Param("feedId") Long feedId);

    @Modifying
    @Query("UPDATE FeedJpaEntity f SET f.bookmarkCount = f.bookmarkCount + 1 WHERE f.id = :feedId")
    void incrementBookmarkCount(@Param("feedId") Long feedId);

    @Modifying
    @Query("UPDATE FeedJpaEntity f SET f.bookmarkCount = f.bookmarkCount - 1 WHERE f.id = :feedId AND f.bookmarkCount > 0")
    void decrementBookmarkCount(@Param("feedId") Long feedId);
}
