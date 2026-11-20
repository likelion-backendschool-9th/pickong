package com.pickong.settlement.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * creator_settlements 테이블 JPA 엔티티.
 * 크리에이터 월별 정산 정보를 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "creator_settlements")
public class CreatorSettlementsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "total_sales", nullable = false)
    private int totalSales;

    @Column(name = "total_fee", nullable = false)
    private int totalFee;

    @Column(name = "net_amount", nullable = false)
    private int netAmount;

    /** 정산 상태. 값 목록: PENDING, CONFIRMED, HELD, PAID */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
