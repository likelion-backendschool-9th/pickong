package com.pickong.auth.domain.vo;

import java.util.Locale;
import java.util.Optional;

/** 지원하는 소셜 로그인. DB(user_social_accounts.provider) · API 값과 같은 대문자입니다. */
public enum SocialProvider {
    KAKAO,
    GOOGLE;

    /** 주소에 쓰는 이름(Spring Security registrationId): kakao · google */
    public String slug() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** registrationId(kakao · google)로 찾습니다. 모르는 값이면 빈 Optional */
    public static Optional<SocialProvider> fromSlug(String registrationId) {
        for (SocialProvider provider : values()) {
            if (provider.slug().equalsIgnoreCase(registrationId)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
