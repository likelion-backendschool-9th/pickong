package com.pickong.plan.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * plan_items 테이블 JPA 엔티티.
 * 일정에 포함된 개별 방문 장소를 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "plan_items")
public class PlanItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 고정 방문 시간. NULL이면 유동 장소. */
    @Column(name = "fixed_time")
    private LocalTime fixedTime;

    @Column(name = "is_visited", nullable = false)
    private boolean isVisited;

    @Column(name = "visited_at")
    private OffsetDateTime visitedAt;

    /** 일정 외 방문 여부. */
    @Column(name = "is_extra", nullable = false)
    private boolean isExtra;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
