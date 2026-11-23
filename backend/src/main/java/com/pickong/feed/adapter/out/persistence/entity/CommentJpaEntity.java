package com.pickong.feed.adapter.out.persistence.entity;

import com.pickong.feed.domain.model.Comment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * comments 테이블 JPA 엔티티.
 * 피드 댓글 및 대댓글을 저장한다.
 * parent_id가 있으면 대댓글이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comments")
public class CommentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feed_id", nullable = false)
    private Long feedId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 대댓글이면 부모 댓글 id. 최상위 댓글이면 NULL. 자기 참조. */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    /** 관리자 숨김 처리 일시. */
    @Column(name = "hidden_at")
    private OffsetDateTime hiddenAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Comment 도메인 객체 → JPA 엔티티 변환 (저장·수정 시 사용)
    public static CommentJpaEntity from(Comment comment) {
        CommentJpaEntity entity = new CommentJpaEntity();
        entity.id = comment.getId();
        entity.feedId = comment.getFeedId();
        entity.userId = comment.getUserId();
        entity.parentId = comment.getParentId();
        entity.content = comment.getContent();
        entity.likeCount = comment.getLikeCount();
        entity.hiddenAt = comment.getHiddenAt();
        entity.deletedAt = comment.getDeletedAt();
        entity.createdAt = comment.getCreatedAt() != null ? comment.getCreatedAt() : OffsetDateTime.now();
        entity.updatedAt = OffsetDateTime.now();
        return entity;
    }

    // deletedAt을 현재 시각으로 설정 — 물리 삭제 대신 소프트 삭제
    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    // JPA 엔티티 → Comment 도메인 객체 변환 (조회 시 사용)
    public Comment toDomain() {
        return toDomainWithReplyCount(0L);
    }

    // 대댓글 개수를 함께 주입하는 변환 — 목록 조회 시 서브쿼리 결과를 받아 설정
    public Comment toDomainWithReplyCount(long replyCount) {
        return Comment.builder()
                .id(id)
                .feedId(feedId)
                .userId(userId)
                .parentId(parentId)
                .content(content)
                .likeCount(likeCount)
                .replyCount(replyCount)
                .hiddenAt(hiddenAt)
                .deletedAt(deletedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
