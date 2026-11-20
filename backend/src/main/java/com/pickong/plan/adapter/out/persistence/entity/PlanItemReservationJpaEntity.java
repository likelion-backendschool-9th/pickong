package com.pickong.plan.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * plan_item_reservations 테이블 JPA 엔티티.
 * 일정 아이템의 예약 정보를 저장한다.
 * plan_item_id는 UNIQUE (1:1 관계, PK ≠ FK).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "plan_item_reservations")
public class PlanItemReservationsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_item_id", nullable = false, unique = true)
    private Long planItemId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "party_size")
    private Integer partySize;

    @Column(name = "reservation_ref")
    private String reservationRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
