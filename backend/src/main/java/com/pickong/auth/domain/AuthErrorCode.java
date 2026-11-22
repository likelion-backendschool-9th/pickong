package com.pickong.auth.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/**
 * 인증(auth) 모듈 오류 코드.
 * 재발급 실패는 모두 401이라 프론트는 로그인 화면으로 보내고, code는 원인 기록 · 안내 문구용입니다.
 */
@Getter
public enum AuthErrorCode implements ErrorCode {

    /** Refresh Token 쿠키가 없거나, DB에 없거나, 만료됨 */
    INVALID_REFRESH_TOKEN(401, "로그인이 필요해요."),
    /** 이미 폐기된 Refresh Token이 다시 옴(탈취 의심) → 그 로그인 세션 전체를 폐기함 */
    REFRESH_TOKEN_REUSED(401, "보안을 위해 로그아웃됐어요. 다시 로그인해 주세요."),
    /** 정지 · 탈퇴 등으로 ACTIVE가 아닌 회원 */
    ACCOUNT_UNAVAILABLE(401, "이용할 수 없는 계정이에요.");

    private final int status;
    private final String message;

    AuthErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
