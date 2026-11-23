package com.pickong.auth.adapter.in.security;

import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.domain.vo.SocialProvider;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 카카오 · Google의 사용자 정보 응답을 제공자와 관계없는 {@link SocialProfile}로 바꿉니다.
 * <p>
 * <b>이메일은 제공자가 '인증됨'이라고 알려 준 것만</b> 씁니다. 인증되지 않은 이메일을 믿으면,
 * 남의 이메일로 먼저 가입해 진짜 주인의 가입을 막는(1인 1계정 규칙 악용) 일이 생길 수 있기 때문입니다.
 */
@Component
public class OAuth2ProfileMapper {

    /**
     * @param registrationId Spring Security의 제공자 이름 (kakao · google)
     * @param user           제공자가 준 사용자 정보 (Google은 OIDC ID 토큰의 내용)
     * @throws IllegalArgumentException 모르는 제공자이거나 고유 ID가 없을 때
     */
    public SocialProfile map(String registrationId, OAuth2User user) {
        SocialProvider provider = SocialProvider.fromSlug(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 제공자: " + registrationId));
        SocialProfile profile = switch (provider) {
            case KAKAO -> kakao(user.getAttributes());
            case GOOGLE -> google(user.getAttributes());
        };
        if (profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new IllegalArgumentException(provider + " 응답에 사용자 ID가 없습니다");
        }
        return profile;
    }

    /**
     * 카카오 응답(GET https://kapi.kakao.com/v2/user/me) 예:
     * <pre>{@code
     * { "id": 4242,
     *   "kakao_account": {
     *     "email": "a@kakao.com", "is_email_valid": true, "is_email_verified": true,
     *     "profile": { "nickname": "콩이", "profile_image_url": "http://k.kakaocdn.net/...", "is_default_image": false } } }
     * }</pre>
     * 이메일은 사용자가 동의하지 않으면 없고, 유효 · 인증 둘 다 true일 때만 씁니다. 기본 프로필 사진이면 사진은 쓰지 않습니다.
     */
    private SocialProfile kakao(Map<String, Object> attributes) {
        Map<String, Object> account = map(attributes.get("kakao_account"));
        Map<String, Object> profile = map(account.get("profile"));
        String email = isTrue(account.get("is_email_valid")) && isTrue(account.get("is_email_verified"))
                ? normalizeEmail(account.get("email"))
                : null;
        String image = isTrue(profile.get("is_default_image")) ? null : https(text(profile.get("profile_image_url")));
        Object id = attributes.get("id");
        return new SocialProfile(SocialProvider.KAKAO, id == null ? null : String.valueOf(id), email,
                text(profile.get("nickname")), image);
    }

    /**
     * Google OIDC 응답(ID 토큰) 예:
     * {@code { "sub": "1089...", "email": "a@gmail.com", "email_verified": true, "name": "Seoyeon", "picture": "https://lh3..." }}
     */
    private SocialProfile google(Map<String, Object> attributes) {
        String email = isTrue(attributes.get("email_verified")) ? normalizeEmail(attributes.get("email")) : null;
        return new SocialProfile(SocialProvider.GOOGLE, text(attributes.get("sub")), email,
                text(attributes.get("name")), https(text(attributes.get("picture"))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** true · "true" 둘 다 참으로 봅니다. (제공자 · 라이브러리에 따라 문자열로 올 때가 있음) */
    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** 대소문자만 다른 같은 이메일로 1인 1계정을 피해 가지 못하게 소문자로 맞춥니다. */
    private static String normalizeEmail(Object value) {
        String email = text(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    /** 카카오는 http 주소를 줄 때가 있어 https로 바꿉니다. (프론트 이미지 허용 목록 · 혼합 콘텐츠 경고 대응) */
    private static String https(String url) {
        if (url == null) {
            return null;
        }
        return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
    }
}
