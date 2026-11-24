package com.pickong.auth.domain.model;

import com.pickong.auth.domain.vo.RefreshTokenState;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 리프레시 토큰 애그리거트. 만료 · 폐기 · 로테이션, 재사용이 감지되면 같은 family 전체를 폐기합니다.
 * <p>
 * <b>family</b>: 한 번의 로그인에서 시작해 로테이션으로 이어진 토큰 묶음(= 기기 하나의 로그인 세션).
 * 로그아웃 · 재사용 감지는 family 단위로 폐기합니다.
 * <p>
 * <b>로테이션</b>: 재발급할 때마다 쓴 토큰은 폐기(revokedAt 기록)하고 같은 family로 새 토큰을 만듭니다.
 * 그래서 폐기된 토큰이 다시 오면 "누군가 복사해 둔 토큰"으로 판단할 수 있습니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken {
    private Long id;
    private Long userId;
    /** 원문이 아닌 SHA-256 해시 */
    private String tokenHash;
    private String family;
    private OffsetDateTime expiresAt;
    /** 폐기 시각. 로테이션으로 바뀌었거나 로그아웃 · 재사용 감지로 폐기되면 값이 있음 */
    private OffsetDateTime revokedAt;
    private OffsetDateTime createdAt;

    /**
     * 새 토큰을 만듭니다. 로그인할 때는 새 family로, 재발급할 때는 이전 토큰의 family를 이어받아 만듭니다.
     * 만료는 항상 '지금 + 수명'이라, 재발급할 때마다 만료가 연장됩니다(슬라이딩).
     */
    public static RefreshToken issue(Long userId, String tokenHash, String family, OffsetDateTime now, Duration ttl) {
        return RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .family(family)
                .expiresAt(now.plus(ttl))
                .createdAt(now)
                .build();
    }

    /**
     * 이 토큰을 지금 쓸 수 있는지 판단합니다.
     * 폐기 여부를 만료보다 먼저 봅니다. 폐기된 토큰이 오면 만료됐더라도 재사용(탈취 의심)으로 처리해야 하기 때문입니다.
     */
    public RefreshTokenState stateAt(OffsetDateTime now) {
        if (revokedAt != null) {
            return RefreshTokenState.REUSED;
        }
        if (!now.isBefore(expiresAt)) {
            return RefreshTokenState.EXPIRED;
        }
        return RefreshTokenState.ACTIVE;
    }
}
