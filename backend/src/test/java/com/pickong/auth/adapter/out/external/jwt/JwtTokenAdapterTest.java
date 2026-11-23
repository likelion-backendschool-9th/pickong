package com.pickong.auth.adapter.out.external.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.security.LoginUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Access Token 발급 · 검증 단위 테스트 (Spring · DB 없이 실행). */
class JwtTokenAdapterTest {

    private static final String SECRET = "test-only-jwt-secret-0123456789-abcdefghij";
    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private JwtTokenAdapter adapterAt(Instant now, String secret) {
        JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(15));
        return new JwtTokenAdapter(new JwtCodec(properties, Clock.fixed(now, ZoneOffset.UTC)), properties);
    }

    private JwtCodec codecAt(Instant now) {
        return new JwtCodec(new JwtProperties(SECRET, Duration.ofMinutes(15)), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("발급한 토큰을 검증하면 회원 id가 나온다")
    void issueAndVerify() {
        JwtTokenAdapter adapter = adapterAt(NOW, SECRET);

        String token = adapter.issueAccessToken(42L);

        assertThat(adapter.verify(token)).contains(new LoginUser(42L));
    }

    @Test
    @DisplayName("15분이 지나면(시계 오차 허용 60초 포함) 만료된 토큰은 인증되지 않는다")
    void expiredTokenRejected() {
        String token = adapterAt(NOW, SECRET).issueAccessToken(42L);

        assertThat(adapterAt(NOW.plus(Duration.ofMinutes(15)), SECRET).verify(token)).isPresent();
        assertThat(adapterAt(NOW.plus(Duration.ofMinutes(17)), SECRET).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("다른 비밀값으로 서명한 토큰은 인증되지 않는다 (위조)")
    void otherSecretRejected() {
        String forged = adapterAt(NOW, "another-secret-another-secret-0123456789").issueAccessToken(42L);

        assertThat(adapterAt(NOW, SECRET).verify(forged)).isEmpty();
    }

    @Test
    @DisplayName("내용(회원 id)을 바꾼 토큰은 서명이 맞지 않아 인증되지 않는다")
    void tamperedPayloadRejected() {
        JwtTokenAdapter adapter = adapterAt(NOW, SECRET);
        String[] parts = adapter.issueAccessToken(42L).split("\\.");
        String otherPayload = adapter.issueAccessToken(1L).split("\\.")[1];

        assertThat(adapter.verify(parts[0] + "." + otherPayload + "." + parts[2])).isEmpty();
    }

    @Test
    @DisplayName("같은 비밀값으로 서명했어도 Access Token이 아닌 종류(임시 가입 · state)는 인증되지 않는다")
    void otherTokenTypesRejected() {
        String signupToken = codecAt(NOW).encode(JwtCodec.TokenType.SIGNUP, "4242", java.util.Map.of(), Duration.ofMinutes(30));
        String stateToken = codecAt(NOW).encode(JwtCodec.TokenType.OAUTH_STATE, "kakao", java.util.Map.of(), Duration.ofMinutes(10));

        assertThat(adapterAt(NOW, SECRET).verify(signupToken)).isEmpty();
        assertThat(adapterAt(NOW, SECRET).verify(stateToken)).isEmpty();
    }

    @Test
    @DisplayName("형식이 아닌 값은 인증되지 않는다")
    void garbageRejected() {
        assertThat(adapterAt(NOW, SECRET).verify("not-a-jwt")).isEmpty();
    }

    @Test
    @DisplayName("비밀값이 없거나 32바이트보다 짧으면 앱이 시작하지 않는다")
    void weakSecretRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtProperties("", Duration.ofMinutes(15))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("short-secret", Duration.ofMinutes(15))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("만료 시간을 정하지 않으면 15분")
    void defaultTtl() {
        assertThat(new JwtProperties(SECRET, null).accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
    }
}
