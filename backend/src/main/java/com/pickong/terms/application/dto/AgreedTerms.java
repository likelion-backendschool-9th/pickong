package com.pickong.terms.application.dto;

import com.pickong.terms.domain.vo.TermsType;
import java.time.OffsetDateTime;

/**
 * 철회하지 않은 동의 기록 한 줄 (저장소 포트의 조회 결과).
 *
 * @param version  동의한 약관 버전
 * @param title    동의한 약관 버전의 제목. 새 버전에서 제목이 바뀌어도 회원이 동의한 그때의 제목입니다
 * @param agreedAt 동의한 시각
 */
public record AgreedTerms(TermsType type, String version, String title, OffsetDateTime agreedAt) {
}
