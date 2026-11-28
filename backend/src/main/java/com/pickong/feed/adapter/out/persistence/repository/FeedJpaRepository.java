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

    // 추천 탭: 다른 사람의 PUBLIC 게시물 최신순 (내 글 제외, 삭제·숨김 제외, cursor 없으면 첫 페이지)
    // viewerId가 null이면 제외할 사람 없이 PUBLIC 전체
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.visibility = 'PUBLIC' " +
           "AND (:viewerId IS NULL OR f.userId <> :viewerId) " +
           "AND f.deletedAt IS NULL " +
           "AND f.hiddenAt IS NULL " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findRecommendedFeeds(
            @Param("viewerId") Long viewerId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 팔로잉 탭: 내 글 전부 + 팔로우한 사람의 PUBLIC, FOLLOWERS 게시물 최신순 (삭제·숨김 제외)
    // 팔로우 목록(followeeIds)은 서비스가 social in-port로 받아 넘김. follows 테이블과 social 엔티티를 직접 읽지 않음 (blueprint 6.1)
    // followeeIds가 비면 IN 조건을 만들 수 없어서 어댑터가 findByOwnerAndVisibilities로 내 글만 조회함
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE (f.userId = :viewerId " +
           "    OR (f.userId IN :followeeIds AND f.visibility IN ('PUBLIC', 'FOLLOWERS'))) " +
           "AND f.deletedAt IS NULL " +
           "AND f.hiddenAt IS NULL " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findFollowingFeeds(
            @Param("viewerId") Long viewerId,
            @Param("followeeIds") List<Long> followeeIds,
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

    // 프로필 피드: 특정 사용자의 게시물 중 공개 범위가 visibilities에 드는 것만 최신순 (삭제·숨김 제외)
    // visibilities는 보는 사람과의 관계로 서비스가 고름 (본인 전체, 팔로워 PUBLIC과 FOLLOWERS, 그 외 PUBLIC)
    @Query("SELECT f FROM FeedJpaEntity f " +
           "WHERE f.userId = :ownerUserId " +
           "AND f.visibility IN :visibilities " +
           "AND f.deletedAt IS NULL " +
           "AND f.hiddenAt IS NULL " +
           "AND (:cursor IS NULL OR f.id < :cursor) " +
           "ORDER BY f.id DESC")
    List<FeedJpaEntity> findByOwnerAndVisibilities(
            @Param("ownerUserId") Long ownerUserId,
            @Param("visibilities") List<String> visibilities,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
