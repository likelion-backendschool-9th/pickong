package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.FeedJpaEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.data.repository.query.Param;

public interface FeedJpaRepository extends JpaRepository<FeedJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FeedJpaEntity f where f.id = :id")
    Optional<FeedJpaEntity> findByIdForUpdate(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FeedJpaEntity f where f.publicId = :publicId")
    Optional<FeedJpaEntity> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE feeds SET hidden_at=:hiddenAt, updated_at=:now
             WHERE id=:id AND deleted_at IS NULL AND hidden_at IS NOT DISTINCT FROM :previous
            """, nativeQuery = true)
    int updateHiddenAt(@Param("id") long id, @Param("previous") OffsetDateTime previous,
            @Param("hiddenAt") OffsetDateTime hiddenAt, @Param("now") OffsetDateTime now);

    List<FeedJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // PUBLIC 피드를 최신순으로 조회 (삭제·숨김 제외, cursor 없으면 첫 페이지)
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.visibility = 'PUBLIC' " +
           "AND f.deletedAt IS NULL " +
           "AND f.hiddenAt IS NULL " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findRecommendedFeeds(
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 팔로잉한 사용자의 PUBLIC 피드를 최신순으로 조회 (삭제·숨김 제외)
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.userId IN (SELECT fw.followeeId FROM FollowJpaEntity fw WHERE fw.followerId = :userId) " +
           "AND f.visibility = 'PUBLIC' " +
           "AND f.deletedAt IS NULL " +
           "AND f.hiddenAt IS NULL " +
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

    // 특정 사용자의 모든 피드 최신순 조회 (본인 — visibility 제한 없음, 삭제·숨김 제외)
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.userId = :ownerUserId " +
           "AND f.deletedAt IS NULL " +
           "AND f.hiddenAt IS NULL " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findAllByOwner(
            @Param("ownerUserId") Long ownerUserId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 특정 사용자의 PUBLIC 피드 최신순 조회 (타인 — PUBLIC만, 삭제·숨김 제외)
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.userId = :ownerUserId " +
           "AND f.visibility = 'PUBLIC' " +
           "AND f.deletedAt IS NULL " +
           "AND f.hiddenAt IS NULL " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findPublicByOwner(
            @Param("ownerUserId") Long ownerUserId,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
