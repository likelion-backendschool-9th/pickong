package com.pickong.collection.domain.model;

import com.pickong.collection.domain.vo.PurchaseStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 컬렉션 구매 애그리거트 (Collection과 별도, collectionId로만 참조). 금액 · 수수료율 · 수수료, ACTIVE/REFUNDED, 첫 열람, 버전, 결제, 정산.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionPurchase {
    private Long id;
    private Long collectionId;
    private Long userId;
    private Long paymentId;
    private Long collectionVersionId;
    private int amount;
    private BigDecimal feeRate;
    private int feeAmount;
    private PurchaseStatus status;
    private OffsetDateTime firstViewedAt;
    private Long settlementId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
