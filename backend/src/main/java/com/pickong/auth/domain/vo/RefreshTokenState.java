package com.pickong.auth.domain.vo;

/** 받은 Refresh Token을 지금 쓸 수 있는지. */
public enum RefreshTokenState {

    /** 쓸 수 있음 → 로테이션(이 토큰을 폐기하고 새 토큰 발급) */
    ACTIVE,
    /** 만료됨 → 다시 로그인 */
    EXPIRED,
    /**
     * 이미 폐기된 토큰이 다시 옴 → 탈취 의심.
     * 로테이션으로 바뀐 토큰은 원래 사용자와 공격자 중 한쪽만 갖고 있어야 하는데, 두 번 쓰였다는 건 누군가 복사했다는 뜻입니다.
     * 그래서 같은 family(그 로그인 세션) 전체를 폐기합니다.
     */
    REUSED
}
