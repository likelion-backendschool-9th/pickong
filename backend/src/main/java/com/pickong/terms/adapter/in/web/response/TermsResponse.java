package com.pickong.terms.adapter.in.web.response;

import com.pickong.terms.application.dto.TermsDocument;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * GET /terms/{type} 응답 (프론트 termsApi.ts의 Terms와 같은 모양).
 * <pre>{@code
 * { "type": "SERVICE", "title": "서비스 이용약관", "required": true, "version": "1.0", "effectiveDate": "2026-11-01",
 *   "sections": [ { "heading": "제1조 (목적)", "body": "..." } ] }
 * }</pre>
 *
 * @param effectiveDate 시행일. 화면에 날짜만 보여 주므로 한국 날짜(yyyy-MM-dd)로 보냅니다
 */
public record TermsResponse(
        String type,
        String title,
        boolean required,
        String version,
        LocalDate effectiveDate,
        List<Section> sections) {

    /** 시행일을 한국 날짜로 바꿀 때 쓰는 시간대. (DB · 서버 시계는 UTC) */
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    /** 조항 하나 */
    public record Section(String heading, String body) {
    }

    public static TermsResponse from(TermsDocument terms) {
        return new TermsResponse(
                terms.type().name(),
                terms.title(),
                terms.required(),
                terms.version(),
                terms.effectiveAt().atZoneSameInstant(KOREA).toLocalDate(),
                terms.sections().stream().map(section -> new Section(section.heading(), section.body())).toList());
    }
}
