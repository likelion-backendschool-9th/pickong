package com.pickong.terms.adapter.in.web.request;

/**
 * PATCH /me/agreements 요청 본문: {@code {"type":"MARKETING","agreed":false}}
 * 종류 검증은 terms 서비스가 하므로 여기서는 받기만 합니다. agreed는 빠지면 안 되므로 Boolean으로 받아 null을 구분합니다.
 */
public record UpdateAgreementRequest(String type, Boolean agreed) {
}
