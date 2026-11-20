package com.pickong.notification.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * notifications 테이블 JPA 엔티티.
 * 사용자 알림을 저장한다.
 * target_id는 여러 테이블을 가리키므로 FK를 걸지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notifications")
public class NotificationsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 알림 종류. 값 목록: FEED_LIKE, COMMENT, FOLLOW, COLLECTION_PURCHASE 등 */
    @Column(name = "type", nullable = false, length = 40)
    private String type;

    /** 행위자 id. 시스템 알림이면 NULL. */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "target_type", length = 30)
    private String targetType;

    /** FK 없음 (여러 테이블 참조). */
    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
