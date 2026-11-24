package com.pickong.auth.application.dto;

import com.pickong.auth.domain.vo.SocialProvider;

/**
 * 찜콩 회원에 연결된 소셜 계정 한 줄(user_social_accounts).
 * 이 테이블은 '있다/없다' 수준의 규칙만 있어서 domain 모델 없이 dto로 주고받습니다. (blueprint 4.2 2단계)
 */
public record LinkedSocialAccount(Long userId, SocialProvider provider, String providerUserId, String email) {
}
