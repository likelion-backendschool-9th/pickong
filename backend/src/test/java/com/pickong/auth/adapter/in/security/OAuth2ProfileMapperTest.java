package com.pickong.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.domain.vo.SocialProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuth2ProfileMapperTest {

    private final OAuth2ProfileMapper mapper = new OAuth2ProfileMapper();

    private static OAuth2User user(Map<String, Object> attributes, String nameKey) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")), attributes, nameKey);
    }

    private static Map<String, Object> kakao(boolean emailValid, boolean emailVerified, boolean defaultImage) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", "콩이");
        profile.put("profile_image_url", "http://k.kakaocdn.net/dn/abc/img.jpg");
        profile.put("is_default_image", defaultImage);
        Map<String, Object> account = new HashMap<>();
        account.put("email", "Kong@Kakao.com");
        account.put("is_email_valid", emailValid);
        account.put("is_email_verified", emailVerified);
        account.put("profile", profile);
        return Map.of("id", 4242L, "kakao_account", account);
    }

    @Test
    @DisplayName("카카오: 회원번호 · 인증된 이메일(소문자) · 닉네임 · https 사진으로 바꾼다")
    void kakaoVerified() {
        SocialProfile profile = mapper.map("kakao", user(kakao(true, true, false), "id"));

        assertThat(profile.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(profile.providerUserId()).isEqualTo("4242");
        assertThat(profile.email()).isEqualTo("kong@kakao.com");
        assertThat(profile.name()).isEqualTo("콩이");
        assertThat(profile.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/dn/abc/img.jpg");
    }

    @Test
    @DisplayName("카카오: 인증 · 유효하지 않은 이메일은 쓰지 않는다 (남의 이메일 선점 방지)")
    void kakaoUnverifiedEmailIgnored() {
        assertThat(mapper.map("kakao", user(kakao(true, false, false), "id")).email()).isNull();
        assertThat(mapper.map("kakao", user(kakao(false, true, false), "id")).email()).isNull();
    }

    @Test
    @DisplayName("카카오: 이메일 동의를 하지 않았으면 이메일 없이, 기본 프로필 사진이면 사진 없이")
    void kakaoNoConsentAndDefaultImage() {
        Map<String, Object> attributes = Map.of("id", 4242L, "kakao_account", Map.of("profile", Map.of("nickname", "콩이", "is_default_image", true)));

        SocialProfile profile = mapper.map("kakao", user(attributes, "id"));

        assertThat(profile.email()).isNull();
        assertThat(profile.profileImageUrl()).isNull();
        assertThat(profile.name()).isEqualTo("콩이");
    }

    @Test
    @DisplayName("Google: sub · 인증된 이메일 · 이름 · 사진")
    void google() {
        Map<String, Object> attributes = Map.of("sub", "1089", "email", "a@gmail.com", "email_verified", true,
                "name", "Seoyeon", "picture", "https://lh3.googleusercontent.com/a/x");

        SocialProfile profile = mapper.map("google", user(attributes, "sub"));

        assertThat(profile).isEqualTo(new SocialProfile(SocialProvider.GOOGLE, "1089", "a@gmail.com", "Seoyeon",
                "https://lh3.googleusercontent.com/a/x"));
    }

    @Test
    @DisplayName("Google: email_verified가 false면 이메일을 쓰지 않는다")
    void googleUnverified() {
        Map<String, Object> attributes = Map.of("sub", "1089", "email", "a@gmail.com", "email_verified", false);

        assertThat(mapper.map("google", user(attributes, "sub")).email()).isNull();
    }

    @Test
    @DisplayName("모르는 제공자는 거절한다")
    void unknownProvider() {
        assertThatThrownBy(() -> mapper.map("naver", user(Map.of("id", "1"), "id"))).isInstanceOf(IllegalArgumentException.class);
    }
}
