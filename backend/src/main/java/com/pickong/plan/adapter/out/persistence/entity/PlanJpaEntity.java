package com.pickong.plan.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * plans 테이블 JPA 엔티티.
 * 사용자의 방문 일정을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "plans")
public class PlanJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    /** 일정 상태. 값 목록: PLANNING, UPCOMING, DONE */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 동행자 정보. JSONB → String 저장. 구조 팀 확인 필요. */
    @Column(name = "companions", columnDefinition = "TEXT")
    private String companions;

    /** 복사 출처 일정 id. 자기 참조. */
    @Column(name = "source_plan_id")
    private Long sourcePlanId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
