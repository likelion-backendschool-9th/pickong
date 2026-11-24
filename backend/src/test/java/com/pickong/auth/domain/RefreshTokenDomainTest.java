package com.pickong.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.auth.domain.model.RefreshToken;
import com.pickong.auth.domain.vo.RawRefreshToken;
import com.pickong.auth.domain.vo.RefreshTokenState;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Refresh Token 도메인 규칙 단위 테스트 (Spring · DB 없이 실행). */
class RefreshTokenDomainTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-23T00:00:00Z");
    private static final Duration TTL = Duration.ofDays(14);

    @Test
    @DisplayName("새 토큰의 만료는 '지금 + 14일'이다 (재발급 때마다 연장되는 슬라이딩)")
    void issueSetsExpiry() {
        RefreshToken token = RefreshToken.issue(1L, "hash", "family-1", NOW, TTL);

        assertThat(token.getExpiresAt()).isEqualTo(NOW.plusDays(14));
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getFamily()).isEqualTo("family-1");
    }

    @Test
    @DisplayName("만료 전에는 ACTIVE, 만료 시각부터는 EXPIRED")
    void activeUntilExpiry() {
        RefreshToken token = RefreshToken.issue(1L, "hash", "family-1", NOW, TTL);

        assertThat(token.stateAt(NOW.plusDays(13))).isEqualTo(RefreshTokenState.ACTIVE);
        assertThat(token.stateAt(NOW.plusDays(14))).isEqualTo(RefreshTokenState.EXPIRED);
    }

    @Test
    @DisplayName("폐기된 토큰은 만료됐더라도 REUSED(탈취 의심)로 본다")
    void revokedIsReusedEvenIfExpired() {
        RefreshToken revoked = RefreshToken.builder()
                .userId(1L).tokenHash("hash").family("family-1")
                .expiresAt(NOW.plusDays(14)).revokedAt(NOW).createdAt(NOW)
                .build();

        assertThat(revoked.stateAt(NOW.plusMinutes(1))).isEqualTo(RefreshTokenState.REUSED);
        assertThat(revoked.stateAt(NOW.plusDays(30))).isEqualTo(RefreshTokenState.REUSED);
    }

    @Test
    @DisplayName("원문은 매번 다르고(256비트 랜덤), 해시는 같은 원문이면 항상 같다(SHA-256, 64자)")
    void rawTokenAndHash() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            values.add(RawRefreshToken.generate().value());
        }
        assertThat(values).hasSize(100);

        RawRefreshToken raw = RawRefreshToken.generate();
        assertThat(raw.value()).hasSize(43);
        assertThat(raw.hash()).hasSize(64).matches("[0-9a-f]+").isEqualTo(new RawRefreshToken(raw.value()).hash());
        assertThat(raw.hash()).isNotEqualTo(RawRefreshToken.generate().hash());
    }
}
