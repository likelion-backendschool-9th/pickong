package com.pickong.auth.adapter.in.web.response;

/** POST /auth/signup 응답: {@code {"username": "seoyeon"}} (토큰은 본문이 아니라 쿠키로) */
public record SignUpResponse(String username) {
}
