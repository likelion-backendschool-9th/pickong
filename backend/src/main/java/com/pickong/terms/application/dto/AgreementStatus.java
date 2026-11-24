package com.pickong.terms.application.dto;

import com.pickong.terms.domain.vo.TermsType;
import java.time.OffsetDateTime;

/**
 * 약관 하나에 대한 내 동의 상태 (설정 > 약관 및 동의 관리의 한 줄).
 *
 * @param version  동의했으면 <b>동의한 버전</b>, 동의하지 않았으면 지금 시행 중인 버전
 * @param agreed   철회하지 않은 동의 기록이 있는지
 * @param agreedAt 동의한 시각. 동의하지 않았으면 null
 */
public record AgreementStatus(
        TermsType type,
        String title,
        boolean required,
        String version,
        boolean agreed,
        OffsetDateTime agreedAt) {
}
