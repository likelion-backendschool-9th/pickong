package com.pickong.auth.adapter.in.web.response;

import com.pickong.auth.application.dto.SocialProfile;

/**
 * GET /auth/signup 응답: 온보딩 화면의 기본값 (프론트 signupApi.ts의 SignupSessionDto와 같은 모양).
 * 소셜 회원번호(providerUserId)는 화면에 필요 없어 내보내지 않습니다.
 */
public record SignupSessionResponse(String provider, String name, String profileImageUrl, String email) {

    public static SignupSessionResponse from(SocialProfile profile) {
        return new SignupSessionResponse(profile.provider().name(), profile.name(), profile.profileImageUrl(), profile.email());
    }
}
