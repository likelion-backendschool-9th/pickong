package com.pickong.payment.domain.vo;

/**
 * 결제 상태.
<p>값 목록은 확정 전입니다 (backend_blueprint.md 8.7 #2).
 */
public enum PaymentStatus {
    READY,
    APPROVING,
    APPROVED,
    FAILED
}
