package com.pickong.terms.adapter.in.web.response;

import com.pickong.terms.application.dto.PendingTerms;

/**
 * GET /me/agreements/pending 응답의 한 줄: 지금 동의를 받아야 하는 약관 (지금 시행 중인 버전).
 * {@code {"type":"PRIVACY","title":"개인정보 수집 · 이용 동의","required":true,"version":"1.1"}}
 */
public record PendingTermsResponse(String type, String title, boolean required, String version) {

    public static PendingTermsResponse from(PendingTerms terms) {
        return new PendingTermsResponse(terms.type().name(), terms.title(), terms.required(), terms.version());
    }
}
