package com.pickong.settlement.domain.model;

import com.pickong.settlement.domain.vo.SettlementStatus;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 크리에이터 월별 정산 애그리거트. PENDING → CONFIRMED → PAID, HELD(보류). 크리에이터 · 월당 1개입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Settlement {
    private Long id;
    private Long creatorId;
    private int periodYear;
    private int periodMonth;
    private int totalSales;
    private int totalFee;
    private int netAmount;
    private SettlementStatus status;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
