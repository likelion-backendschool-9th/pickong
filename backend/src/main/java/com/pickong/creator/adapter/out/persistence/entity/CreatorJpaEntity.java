package com.pickong.creator.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * creators 테이블 JPA 엔티티.
 * 크리에이터 계정 정보를 저장한다.
 * user_id는 UNIQUE (1:1 관계).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "creators")
public class CreatorJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 크리에이터 상태. 값 목록: PENDING, APPROVED, REJECTED, SUSPENDED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "grade_id")
    private Long gradeId;

    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
