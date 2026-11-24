package com.pickong.auth.application.port.in;

import com.pickong.auth.application.dto.IssuedTokens;

/**
 * 로그인 세션(토큰) 유스케이스.
 * <ul>
 *   <li>{@link #issue}: 로그인 · 가입 완료 때 새 세션 발급 (4~5단계의 OAuth2 성공 처리 · 온보딩이 호출)</li>
 *   <li>{@link #refresh}: Access Token 재발급 + Refresh Token 로테이션 (POST /auth/refresh)</li>
 *   <li>{@link #logout}: 이 기기의 세션 폐기 (POST /auth/logout)</li>
 *   <li>{@link #deleteExpiredTokens}: 오래전에 만료된 토큰 행 정리 (매일 정리 작업이 호출)</li>
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

    /**
     * 만료된 지 보관 기간(기본 7일)이 지난 Refresh Token 행을 지웁니다. 폐기 여부와 관계없이 만료 기준으로만 지웁니다.
     * 여러 번 실행해도 결과가 같아서, 서버가 여러 대여도 따로 잠금 없이 돌려도 됩니다.
     *
     * @return 지운 행 수
     */
    int deleteExpiredTokens();

    /** 한 회원의 살아 있는 세션(Refresh Token)을 모두 폐기합니다. 탈퇴 신청 직후 부릅니다(모든 기기에서 로그아웃). */
    void revokeAllSessions(Long userId);

    /**
     * 한 회원의 토큰 행을 모두 지웁니다. 익명화 트랜잭션 안에서 부릅니다(UserWithdrawnEvent 구독).
     * 이미 없으면 아무것도 하지 않아 여러 번 불려도 됩니다.
     */
    void deleteAllTokens(Long userId);
}
