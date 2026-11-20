package com.pickong.payment.domain.model;

import com.pickong.payment.domain.vo.RefundStatus;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 환불 애그리거트. REQUESTED → COMPLETED | FAILED. 요청자 또는 처리자가 있어야 하고, COMPLETED면 PG 취소 키가 있어야 합니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Refund {
    private Long id;
    private Long paymentId;
    private String reason;
    private RefundStatus status;
    private Long requestedBy;
    private Long handledBy;
    private String pgCancelKey;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
