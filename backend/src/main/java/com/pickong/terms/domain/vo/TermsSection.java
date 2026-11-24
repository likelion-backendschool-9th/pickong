package com.pickong.terms.domain.vo;

/**
 * 약관 본문의 조항 하나. 약관 버전 한 개에 순서대로 묶여 있습니다.
 *
 * @param heading 조항 제목 (예: "제1조 (목적)")
 * @param body    조항 내용. 줄바꿈(\n)이 들어 있을 수 있습니다
 */
public record TermsSection(String heading, String body) {
}
