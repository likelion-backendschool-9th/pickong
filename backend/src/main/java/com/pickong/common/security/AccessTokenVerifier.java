package com.pickong.common.security;

import java.time.Instant;
import java.util.Optional;

/**
 * Access Token 검증. 구현은 auth 모듈(JWT 어댑터)에 있고,
 * HTTP 인증 필터와 이후 WebSocket 연결 인증이 같은 검증기를 씁니다.
 */
public interface AccessTokenVerifier {

    /** 서명 · 만료가 올바르면 로그인 회원을, 아니면(만료 · 위조 · 형식 오류) 빈 값을 돌려줍니다. */
    Optional<LoginUser> verify(String accessToken);

    /**
     * 올바른 토큰의 만료 시각. WebSocket 연결은 이 시각이 지나면 닫습니다 (blueprint 5.4.3).
     *
     * @return 서명 · 만료가 올바르지 않으면 빈 값
     */
    Optional<Instant> expiresAt(String accessToken);
}
