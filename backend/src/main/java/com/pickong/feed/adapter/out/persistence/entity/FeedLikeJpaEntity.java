package com.pickong.feed.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * feed_likes 테이블 JPA 엔티티.
 * 피드 좋아요를 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "feed_likes")
public class FeedLikeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feed_id", nullable = false)
    private Long feedId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static FeedLikeJpaEntity of(Long feedId, Long userId) {
        FeedLikeJpaEntity entity = new FeedLikeJpaEntity();
        entity.feedId = feedId;
        entity.userId = userId;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }
}
