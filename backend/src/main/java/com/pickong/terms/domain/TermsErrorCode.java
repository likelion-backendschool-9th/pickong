package com.pickong.terms.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 약관(terms) 모듈 오류 코드. */
@Getter
public enum TermsErrorCode implements ErrorCode {

    /** 필수 약관(서비스 이용약관 · 개인정보) 중 동의하지 않은 것이 있음 */
    REQUIRED_TERMS_NOT_AGREED(400, "필수 약관에 동의해 주세요."),
    /** 모르는 약관 종류가 옴 */
    UNKNOWN_TERMS(400, "알 수 없는 약관이에요."),
    /** 필수 약관은 설정에서 철회하거나 다시 동의할 수 없음 (해지는 탈퇴로만) */
    REQUIRED_TERMS_CANNOT_BE_REVOKED(400, "필수 약관은 철회할 수 없어요. 더 이상 이용하지 않으려면 회원 탈퇴를 진행해 주세요."),
    /** 약관 전문 조회: 없는 종류이거나 시행 중인 버전이 없음 */
    TERMS_NOT_FOUND(404, "약관을 찾을 수 없어요.");

    private final int status;
    private final String message;

    TermsErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
