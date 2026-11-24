package com.pickong.terms.application.dto;

import com.pickong.terms.domain.vo.TermsSection;
import com.pickong.terms.domain.vo.TermsType;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 약관 전문: 지금 시행 중인 버전의 정보와 조항.
 *
 * @param effectiveAt 시행 일시
 * @param sections    조항 (순서대로)
 */
public record TermsDocument(
        TermsType type,
        String title,
        boolean required,
        String version,
        OffsetDateTime effectiveAt,
        List<TermsSection> sections) {
}
