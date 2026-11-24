package com.pickong.auth.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 쿠키 환경 설정 (pickong.auth.cookie.*). 로컬과 운영을 코드 수정 없이 환경변수로 나눕니다.
 *
 * @param secure HTTPS에서만 쿠키를 보내는지. 기본 true(운영). 로컬(http://localhost)만 COOKIE_SECURE=false
 * @param domain 쿠키 Domain. 기본 비움 = 지금 도메인에만 저장.
 *               웹소켓 등을 백엔드 하위 도메인으로 직접 붙이게 되면 COOKIE_DOMAIN=.pickong.app처럼 넣습니다
 */
@ConfigurationProperties("pickong.auth.cookie")
public record AuthCookieProperties(Boolean secure, String domain) {

    public AuthCookieProperties {
        // 설정을 빠뜨려도 안전한 쪽(Secure 켜짐)으로 동작하게 합니다.
        if (secure == null) {
            secure = true;
        }
        if (domain != null && domain.isBlank()) {
            domain = null;
        }
    }
}
