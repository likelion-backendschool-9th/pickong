package com.pickong.auth.domain.model;

import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 리프레시 토큰 애그리거트. 만료 · 폐기 · 로테이션, 재사용이 감지되면 같은 family 전체를 폐기합니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken {
    private Long id;
    private Long userId;
    private String tokenHash;
    private String family;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime createdAt;
}
