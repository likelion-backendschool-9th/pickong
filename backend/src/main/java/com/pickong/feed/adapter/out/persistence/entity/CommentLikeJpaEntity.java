package com.pickong.feed.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * comment_likes 테이블 JPA 엔티티.
 * 댓글 좋아요를 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comment_likes")
public class CommentLikeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // 댓글 좋아요 생성 — commentId와 userId를 받아 엔티티를 만듦
    public static CommentLikeJpaEntity create(Long commentId, Long userId) {
        CommentLikeJpaEntity entity = new CommentLikeJpaEntity();
        entity.commentId = commentId;
        entity.userId = userId;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }
}
