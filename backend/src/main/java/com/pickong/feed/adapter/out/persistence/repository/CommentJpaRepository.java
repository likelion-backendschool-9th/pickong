package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.CommentJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface CommentJpaRepository extends JpaRepository<CommentJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CommentJpaEntity c where c.id=:id")
    Optional<CommentJpaEntity> findByIdForUpdate(@Param("id") long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE comments SET hidden_at=:hiddenAt, updated_at=:now
             WHERE id=:id AND deleted_at IS NULL AND hidden_at IS NOT DISTINCT FROM :previous
            """, nativeQuery = true)
    int updateHiddenAt(@Param("id") long id, @Param("previous") OffsetDateTime previous,
            @Param("hiddenAt") OffsetDateTime hiddenAt, @Param("now") OffsetDateTime now);

    // feedId에 속한 최상위 댓글 + 대댓글 개수 — cursor(commentId) 이전 최신순으로 조회
    // 삭제된 댓글도 살아있는 대댓글이 있으면 포함 (대댓글 고아 방지)
    // Object[0] = CommentJpaEntity, Object[1] = 대댓글 수(Long)
    // JPQL은 LIMIT를 직접 지원하지 않으므로 Pageable로 개수 제한
    @Query("SELECT c, (SELECT COUNT(r) FROM CommentJpaEntity r WHERE r.parentId = c.id " +
            "  AND r.deletedAt IS NULL AND (r.hiddenAt IS NULL OR r.userId=:viewer OR :admin=true)) " +
            "FROM CommentJpaEntity c WHERE c.feedId = :feedId AND c.parentId IS NULL " +
            "AND (:cursor IS NULL OR c.id < :cursor) AND (c.hiddenAt IS NULL OR c.userId=:viewer OR :admin=true) " +
            "AND (c.deletedAt IS NULL OR EXISTS (" +
            "  SELECT r FROM CommentJpaEntity r WHERE r.parentId = c.id AND r.deletedAt IS NULL AND (r.hiddenAt IS NULL OR r.userId=:viewer OR :admin=true)" +
            ")) ORDER BY c.id DESC")
    List<Object[]> findTopLevelByFeedId(@Param("feedId") Long feedId,
            @Param("cursor") Long cursor,
            @Param("viewer") Long viewer,
            @Param("admin") boolean admin,
            Pageable pageable);

    // 대댓글 — parentId 기준 오래된순으로 조회 (대화 흐름 유지)
    // cursor 이후(id > cursor)를 오래된순으로 조회
    @Query("SELECT c FROM CommentJpaEntity c WHERE c.parentId = :parentId " +
            "AND (:cursor IS NULL OR c.id > :cursor) AND c.deletedAt IS NULL AND (c.hiddenAt IS NULL OR c.userId=:viewer OR :admin=true) " +
            "ORDER BY c.id ASC")
    List<CommentJpaEntity> findRepliesByParentId(@Param("parentId") Long parentId,
            @Param("cursor") Long cursor,
            @Param("viewer") Long viewer,
            @Param("admin") boolean admin,
            Pageable pageable);

    @Modifying
    @Query("UPDATE CommentJpaEntity c SET c.likeCount = c.likeCount + 1 WHERE c.id = :commentId")
    void incrementLikeCount(@Param("commentId") Long commentId);

    @Modifying
    @Query("UPDATE CommentJpaEntity c SET c.likeCount = c.likeCount - 1 WHERE c.id = :commentId AND c.likeCount > 0")
    void decrementLikeCount(@Param("commentId") Long commentId);
}
