package com.pickong.common.error;

import lombok.Getter;

/** 특정 모듈에 속하지 않는 공통 오류 (입력 검증, 인증 · 인가). */
@Getter
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(400, "입력값을 확인해 주세요."),
    /** 로그인하지 않았거나 Access Token이 없거나 만료 · 위조됨. 프론트는 재발급을 한 번 시도합니다. */
    UNAUTHORIZED(401, "로그인이 필요해요."),
    /** 로그인했지만 권한이 없음 (예: 다른 사람의 게시물 수정) */
    FORBIDDEN(403, "권한이 없어요."),
    /** 상태를 바꾸는 요청에 CSRF 토큰이 없거나 맞지 않음. 프론트는 XSRF-TOKEN 쿠키를 다시 읽어 한 번 재시도합니다. */
    INVALID_CSRF_TOKEN(403, "요청을 확인할 수 없어요. 페이지를 새로고침한 뒤 다시 시도해 주세요.");

    private final int status;
    private final String message;

    CommonErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
