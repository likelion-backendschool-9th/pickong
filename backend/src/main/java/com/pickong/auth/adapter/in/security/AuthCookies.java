package com.pickong.auth.adapter.in.security;

/**
 * 인증 쿠키 이름 · 경로. 이름은 프론트 mock(frontend/mocks/handlers/auth.ts)과 같습니다.
 * 쿠키를 쓰고 지우는 일은 {@link TokenCookieWriter}가 합니다.
 */
public final class AuthCookies {

    /** Access Token(JWT). 모든 API에 필요해서 Path=/ */
    public static final String ACCESS_TOKEN = "pickong_at";
    public static final String ACCESS_TOKEN_PATH = "/";

    /** Refresh Token. 재발급 · 로그아웃 같은 인증 API에만 실리도록 Path=/api/v1/auth */
    public static final String REFRESH_TOKEN = "pickong_rt";
    public static final String REFRESH_TOKEN_PATH = "/api/v1/auth";

    private AuthCookies() {
    }
}
