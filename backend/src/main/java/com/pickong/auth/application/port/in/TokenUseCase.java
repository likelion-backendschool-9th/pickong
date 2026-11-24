package com.pickong.auth.application.port.in;

import com.pickong.auth.application.dto.IssuedTokens;

/**
 * 로그인 세션(토큰) 유스케이스.
 * <ul>
 *   <li>{@link #issue}: 로그인 · 가입 완료 때 새 세션 발급 (4~5단계의 OAuth2 성공 처리 · 온보딩이 호출)</li>
 *   <li>{@link #refresh}: Access Token 재발급 + Refresh Token 로테이션 (POST /auth/refresh)</li>
 *   <li>{@link #logout}: 이 기기의 세션 폐기 (POST /auth/logout)</li>
 * </ul>
 */
public interface TokenUseCase {

    /** 새 로그인 세션(새 family)을 만들고 Access · Refresh Token을 발급합니다. */
    IssuedTokens issue(Long userId);

    /**
     * Refresh Token으로 새 토큰 쌍을 발급하고, 받은 Refresh Token은 폐기합니다.
     *
     * @throws com.pickong.common.error.BusinessException 401 — 토큰이 없거나 만료(INVALID_REFRESH_TOKEN),
     *         이미 폐기된 토큰 재사용(REFRESH_TOKEN_REUSED, 세션 전체 폐기), 정지 · 탈퇴 회원(ACCOUNT_UNAVAILABLE, 세션 폐기)
     */
    IssuedTokens refresh(String rawRefreshToken);

    /** 이 Refresh Token의 세션(family)을 폐기합니다. 토큰이 없거나 이미 폐기됐어도 오류 없이 끝납니다. */
    void logout(String rawRefreshToken);
}
