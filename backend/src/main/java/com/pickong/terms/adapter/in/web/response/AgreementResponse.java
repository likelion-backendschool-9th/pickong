package com.pickong.terms.adapter.in.web.response;

import com.pickong.terms.application.dto.AgreementStatus;
import java.time.OffsetDateTime;

/**
 * GET · PATCH /me/agreements 응답의 한 줄 (프론트 accountApi.ts의 Agreement와 같은 모양).
 * {@code {"type":"MARKETING","title":"마케팅 정보 수신","required":false,"version":"1.0","agreed":true,"agreedAt":"2026-11-24T09:00:00Z"}}
 */
public record AgreementResponse(
        String type,
        String title,
        boolean required,
        String version,
        boolean agreed,
        OffsetDateTime agreedAt) {

    public static AgreementResponse from(AgreementStatus status) {
        return new AgreementResponse(status.type().name(), status.title(), status.required(), status.version(),
                status.agreed(), status.agreedAt());
    }
}
