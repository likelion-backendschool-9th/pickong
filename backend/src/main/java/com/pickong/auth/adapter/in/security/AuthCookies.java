package com.pickong.auth.adapter.in.security;

/**
 * 인증 쿠키 이름. 프론트 mock(frontend/mocks/handlers/auth.ts)과 같은 이름을 씁니다.
 * 쿠키를 쓰는 쪽(속성 HttpOnly · Secure · SameSite=Lax · Path)은 토큰 발급 단계에서 추가합니다.
 */
public final class AuthCookies {

    /** Access Token(JWT). Path=/ */
    public static final String ACCESS_TOKEN = "pickong_at";

    private AuthCookies() {
    }
}
