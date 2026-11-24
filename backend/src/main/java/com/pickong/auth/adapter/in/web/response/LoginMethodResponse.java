package com.pickong.auth.adapter.in.web.response;

import com.pickong.auth.application.dto.LoginMethod;
import java.time.OffsetDateTime;

/**
 * GET /me/social-accounts 응답의 한 줄 (프론트 accountApi.ts의 SocialAccount + canUnlink).
 * {@code {"provider":"KAKAO","email":"kong@kakao.com","connectedAt":"2026-11-23T09:00:00+09:00","canUnlink":false}}
 */
public record LoginMethodResponse(String provider, String email, OffsetDateTime connectedAt, boolean canUnlink) {

    public static LoginMethodResponse from(LoginMethod method) {
        return new LoginMethodResponse(method.provider().name(), method.email(), method.connectedAt(), method.canUnlink());
    }
}
