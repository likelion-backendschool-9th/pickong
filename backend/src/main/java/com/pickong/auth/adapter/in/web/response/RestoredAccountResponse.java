package com.pickong.auth.adapter.in.web.response;

/** POST /auth/withdrawal/cancel 응답: {@code {"username":"seoyeon"}} (가입 완료 응답과 같은 모양) */
public record RestoredAccountResponse(String username) {
}
