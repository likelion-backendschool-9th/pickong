package com.pickong.moderation.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * user_sanctions 테이블 JPA 엔티티.
 * 사용자 제재 이력을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_sanctions")
public class UserSanctionsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 제재 유형. 값 목록: WARNING, SUSPEND, BAN */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** 정지 해제 일시. SUSPEND 제재 시에만 값이 있음. */
    @Column(name = "suspended_until")
    private OffsetDateTime suspendedUntil;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    /** 처리한 관리자 id. admins 참조 (FK 여부 팀 확인). */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
