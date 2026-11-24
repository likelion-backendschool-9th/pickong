package com.pickong.auth.adapter.out.external.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.domain.vo.SocialProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignupTokenAdapterTest {

    private static final String SECRET = "test-only-jwt-secret-0123456789-abcdefghij";
    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, Duration.ofMinutes(15));

    private static JwtCodec codecAt(Instant now) {
        return new JwtCodec(PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("발급한 임시 가입 토큰을 검증하면 소셜 계정 정보가 그대로 돌아온다")
    void roundTrip() {
        SocialProfile profile = new SocialProfile(SocialProvider.KAKAO, "4242", "kong@kakao.com", "콩이", "https://k.kakaocdn.net/a.jpg");
        SignupTokenAdapter adapter = new SignupTokenAdapter(codecAt(NOW));

        assertThat(adapter.verify(adapter.issue(profile))).contains(profile);
    }

    @Test
    @DisplayName("값이 없는 항목(이메일 미동의 등)은 비어 있는 채로 돌아온다")
    void missingFields() {
        SocialProfile profile = new SocialProfile(SocialProvider.GOOGLE, "1089", null, null, null);
        SignupTokenAdapter adapter = new SignupTokenAdapter(codecAt(NOW));

        assertThat(adapter.verify(adapter.issue(profile))).contains(profile);
    }

    @Test
    @DisplayName("30분이 지나면 쓸 수 없다")
    void expires() {
        String token = new SignupTokenAdapter(codecAt(NOW)).issue(new SocialProfile(SocialProvider.KAKAO, "4242", null, null, null));

        assertThat(new SignupTokenAdapter(codecAt(NOW.plus(Duration.ofMinutes(29)))).verify(token)).isPresent();
        assertThat(new SignupTokenAdapter(codecAt(NOW.plus(Duration.ofMinutes(32)))).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("Access Token은 임시 가입 토큰으로 쓸 수 없다 (토큰 종류가 다름)")
    void accessTokenRejected() {
        String accessToken = new JwtTokenAdapter(codecAt(NOW), PROPERTIES).issueAccessToken(42L);

        assertThat(new SignupTokenAdapter(codecAt(NOW)).verify(accessToken)).isEmpty();
    }
}
