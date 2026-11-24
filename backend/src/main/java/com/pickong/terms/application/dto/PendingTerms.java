package com.pickong.terms.application.dto;

import com.pickong.terms.domain.vo.TermsType;

/**
 * 지금 동의를 받아야 하는 약관 하나 (재동의 화면의 한 줄). 항상 지금 시행 중인 버전입니다.
 *
 * @param required 필수 약관이면 동의할 때까지 서비스를 쓸 수 없습니다. 선택 약관은 예전에 동의했던 회원에게만 다시 묻습니다
 */
public record PendingTerms(TermsType type, String title, boolean required, String version) {
}
