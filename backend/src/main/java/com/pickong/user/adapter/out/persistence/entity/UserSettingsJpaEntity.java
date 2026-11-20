package com.pickong.user.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * user_settings 테이블 JPA 엔티티.
 * 1:1 테이블로 PK = FK (user_id).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_settings")
public class UserSettingsJpaEntity {

    /** PK = FK (users.id) */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "notification_feed_like", nullable = false)
    private boolean notificationFeedLike;

    @Column(name = "notification_comment", nullable = false)
    private boolean notificationComment;

    @Column(name = "notification_follow", nullable = false)
    private boolean notificationFollow;

    /** 지도 범위 설정. 값 목록 팀 확인 필요. */
    @Column(name = "map_scope")
    private String mapScope;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
