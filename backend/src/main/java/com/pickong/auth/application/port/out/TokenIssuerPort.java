package com.pickong.auth.application.port.out;

/**
 * Access Token 발급. 로그인 · 가입 완료 · 재발급 유스케이스(3~4단계)가 이 포트로 토큰을 만듭니다.
 * 구현: {@code auth.adapter.out.external.jwt.JwtTokenAdapter}
 */
public interface TokenIssuerPort {

    /** 회원의 Access Token(JWT)을 발급합니다. 만료 시간은 설정(pickong.auth.jwt.access-token-ttl)을 따릅니다. */
    String issueAccessToken(Long userId);
}
