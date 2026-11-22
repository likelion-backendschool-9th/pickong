package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.CommentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentJpaRepository extends JpaRepository<CommentJpaEntity, Long> {

    // feedId에 속한 최상위 댓글 — cursor(commentId) 이후 오래된 순으로 limit개 조회
    @Query("SELECT c FROM CommentJpaEntity c WHERE c.feedId = :feedId AND c.parentId IS NULL " +
            "AND (:cursor IS NULL OR c.id > :cursor) AND c.deletedAt IS NULL " +
            "ORDER BY c.id ASC LIMIT :limit")
    List<CommentJpaEntity> findTopLevelByFeedId(@Param("feedId") Long feedId,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    // 대댓글 — parentId 기준 오래된 순으로 limit개 조회
    @Query("SELECT c FROM CommentJpaEntity c WHERE c.parentId = :parentId " +
            "AND (:cursor IS NULL OR c.id > :cursor) AND c.deletedAt IS NULL " +
            "ORDER BY c.id ASC LIMIT :limit")
    List<CommentJpaEntity> findRepliesByParentId(@Param("parentId") Long parentId,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);
}
