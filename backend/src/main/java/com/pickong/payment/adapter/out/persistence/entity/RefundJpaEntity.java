package com.pickong.payment.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * refunds 테이블 JPA 엔티티.
 * 환불 이력을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refunds")
public class RefundJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    /** 환불 사유. RefundReason VO. */
    @Column(name = "reason", nullable = false)
    private String reason;

    /** 환불 상태. 값 목록: REQUESTED, COMPLETED, FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    /** 관리자가 처리한 경우 관리자 id. FK 여부 팀 확인. */
    @Column(name = "handled_by")
    private Long handledBy;

    /** PG 취소 키. COMPLETED 상태일 때 세팅. */
    @Column(name = "pg_cancel_key")
    private String pgCancelKey;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
