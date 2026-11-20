package com.pickong.payment.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * payments 테이블 JPA 엔티티.
 * 결제 이력을 저장한다.
 * target_id는 여러 테이블을 가리키므로 FK를 걸지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payments")
public class PaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 멱등 키. 64자 이하. OrderId VO. */
    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 결제 목적. 값 목록: COLLECTION 등 */
    @Column(name = "purpose", nullable = false, length = 30)
    private String purpose;

    /** FK 없음 (다른 테이블 참조). */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "amount", nullable = false)
    private int amount;

    /** 결제 상태. 값 목록: READY, APPROVING, APPROVED, FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** PG 승인 키. APPROVED 상태일 때 세팅. */
    @Column(name = "payment_key")
    private String paymentKey;

    @Column(name = "method")
    private String method;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "failed_code")
    private String failedCode;

    @Column(name = "failed_message")
    private String failedMessage;

    /** PG 원본 응답. JSONB → String 저장. */
    @Column(name = "pg_raw_response", columnDefinition = "TEXT")
    private String pgRawResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
