/**
 * PG 승인 · 취소 · 조회 (PaymentGatewayPort 구현). 지금은 테스트 결제 어댑터, PG 확정 후 실제 어댑터를 추가합니다.
 * 승인 · 취소 요청에는 멱등 키(orderId · refundId)를 전달합니다.
 */
package com.pickong.payment.adapter.out.external.pg;
