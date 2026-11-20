package com.pickong.payment.domain.model;

import com.pickong.payment.domain.vo.PaymentPurpose;
import com.pickong.payment.domain.vo.PaymentStatus;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 결제 애그리거트. READY → APPROVING → APPROVED | FAILED.
 * <p>orderId는 멱등 키(64자 이하), targetId는 purpose에 따라 다른 테이블을 가리킵니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment {
    private Long id;
    private String orderId;
    private Long userId;
    private PaymentPurpose purpose;
    private Long targetId;
    private int amount;
    private PaymentStatus status;
    private String paymentKey;
    private String method;
    private OffsetDateTime approvedAt;
    private String failedCode;
    private String failedMessage;
    private String pgRawResponse;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
