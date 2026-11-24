package com.pickong.terms.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 약관(terms) 모듈 오류 코드. */
@Getter
public enum TermsErrorCode implements ErrorCode {

    /** 필수 약관(서비스 이용약관 · 개인정보) 중 동의하지 않은 것이 있음 */
    REQUIRED_TERMS_NOT_AGREED(400, "필수 약관에 동의해 주세요."),
    /** 모르는 약관 종류가 옴 */
    UNKNOWN_TERMS(400, "알 수 없는 약관이에요.");

    private final int status;
    private final String message;

    TermsErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
