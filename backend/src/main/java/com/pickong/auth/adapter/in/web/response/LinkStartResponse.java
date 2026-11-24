package com.pickong.auth.adapter.in.web.response;

/**
 * POST /me/social-accounts/{provider} 응답: 브라우저가 이동할 연결 시작 주소(같은 출처의 경로).
 * {@code {"authorizationUrl":"/api/v1/auth/oauth2/authorization/google?link=..."}}
 */
public record LinkStartResponse(String authorizationUrl) {
}
