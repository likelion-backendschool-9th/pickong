package com.pickong.auth.application.dto;

import com.pickong.auth.domain.vo.SocialProvider;

/**
 * 카카오 · Google 응답을 제공자와 관계없이 같은 모양으로 바꾼 소셜 계정 정보.
 *
 * @param provider        제공자
 * @param providerUserId  제공자의 고유 사용자 식별자 (카카오 회원번호, Google sub). 회원을 찾는 기준
 * @param email           <b>제공자가 인증했다고 알려 준</b> 이메일만. 인증되지 않았거나 동의하지 않았으면 null
 * @param name            소셜 계정의 이름(닉네임). 온보딩의 이름 기본값으로 씁니다. 없으면 null
 * @param profileImageUrl 소셜 프로필 사진 주소(https). 기본 이미지거나 없으면 null
 */
public record SocialProfile(SocialProvider provider, String providerUserId, String email, String name, String profileImageUrl) {

    /** 로그에 이메일 · 이름이 남지 않게 합니다. */
    @Override
    public String toString() {
        return "SocialProfile[" + provider + ", " + providerUserId + "]";
    }
}
