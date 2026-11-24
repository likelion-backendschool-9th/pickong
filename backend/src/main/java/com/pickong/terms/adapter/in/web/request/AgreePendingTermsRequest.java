package com.pickong.terms.adapter.in.web.request;

import java.util.List;

/**
 * POST /me/agreements 요청 본문 (재동의 화면): {@code {"agreedTerms":["SERVICE","PRIVACY"]}}
 * 가입 요청(SignUpRequest.agreedTerms)과 같은 모양입니다. 빠지면 빈 목록으로 봅니다(동의 받을 필수 약관이 있으면 400).
 */
public record AgreePendingTermsRequest(List<String> agreedTerms) {
}
