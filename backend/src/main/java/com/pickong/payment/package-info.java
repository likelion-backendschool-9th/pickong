/**
 * 결제 모듈: 결제 준비 · 승인 · 실패, PG 연동, 환불 실행 · 기록, 결제 대사. 컬렉션을 모르며, 목적별 처리는 PaymentPurposeHandler(SPI)로 위임합니다.
 *
 * <p>소유 테이블: payments, refunds
 * <p>의존 모듈: 없음
 * <p>설계: backend_blueprint.md 5.2.3
 */
package com.pickong.payment;
