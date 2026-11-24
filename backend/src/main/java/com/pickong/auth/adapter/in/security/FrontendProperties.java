package com.pickong.auth.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프론트 주소 설정 (pickong.frontend.*).
 *
 * @param baseUrl 프론트 기준 주소. 로컬 http://localhost:3000, 운영 https://프론트도메인 (환경변수 FRONTEND_BASE_URL).
 *                두 곳에 씁니다. ① 카카오 · Google에 등록하는 콜백 주소({baseUrl}/api/v1/auth/oauth2/callback/{제공자})
 *                ② 로그인 성공 · 실패 후 보낼 화면 주소({baseUrl}/feed 등)
 */
@ConfigurationProperties("pickong.frontend")
public record FrontendProperties(String baseUrl) {

    public FrontendProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:3000";
        }
        // 끝의 /를 지워 "{baseUrl}/feed"가 "//feed"가 되지 않게 합니다.
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** 프론트 화면의 전체 주소. 예: url("/feed") → http://localhost:3000/feed */
    public String url(String path) {
        return baseUrl + path;
    }
}
