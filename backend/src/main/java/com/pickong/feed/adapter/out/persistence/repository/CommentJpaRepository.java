package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.CommentJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentJpaRepository extends JpaRepository<CommentJpaEntity, Long> {

    // feedId에 속한 최상위 댓글 + 대댓글 개수 — cursor(commentId) 이전 최신순으로 조회
    // 삭제된 댓글도 살아있는 대댓글이 있으면 포함 (대댓글 고아 방지)
    // Object[0] = CommentJpaEntity, Object[1] = 대댓글 수(Long)
    // JPQL은 LIMIT를 직접 지원하지 않으므로 Pageable로 개수 제한
    @Query("SELECT c, (SELECT COUNT(r) FROM CommentJpaEntity r WHERE r.parentId = c.id " +
            "  AND r.deletedAt IS NULL AND r.hiddenAt IS NULL) " +
            "FROM CommentJpaEntity c WHERE c.feedId = :feedId AND c.parentId IS NULL " +
            "AND (:cursor IS NULL OR c.id < :cursor) AND c.hiddenAt IS NULL " +
            "AND (c.deletedAt IS NULL OR EXISTS (" +
            "  SELECT r FROM CommentJpaEntity r WHERE r.parentId = c.id AND r.deletedAt IS NULL AND r.hiddenAt IS NULL" +
            ")) ORDER BY c.id DESC")
    List<Object[]> findTopLevelByFeedId(@Param("feedId") Long feedId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 대댓글 — parentId 기준 오래된순으로 조회 (대화 흐름 유지)
    // cursor 이후(id > cursor)를 오래된순으로 조회
    @Query("SELECT c FROM CommentJpaEntity c WHERE c.parentId = :parentId " +
            "AND (:cursor IS NULL OR c.id > :cursor) AND c.deletedAt IS NULL AND c.hiddenAt IS NULL " +
            "ORDER BY c.id ASC")
    List<CommentJpaEntity> findRepliesByParentId(@Param("parentId") Long parentId,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
