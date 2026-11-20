package com.pickong.collection.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * collection_purchases 테이블 JPA 엔티티.
 * 유료 컬렉션 구매 이력을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "collection_purchases")
public class CollectionPurchasesJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_id", nullable = false)
    private Long collectionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "collection_version_id", nullable = false)
    private Long collectionVersionId;

    @Column(name = "amount", nullable = false)
    private int amount;

    /** 수수료율. 0.30~0.35. FeeRate VO. */
    @Column(name = "fee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal feeRate;

    @Column(name = "fee_amount", nullable = false)
    private int feeAmount;

    /** 구매 상태. 값 목록: ACTIVE, REFUNDED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "first_viewed_at")
    private OffsetDateTime firstViewedAt;

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
