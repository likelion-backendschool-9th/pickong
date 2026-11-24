package com.pickong.auth.application.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh Token 설정 (pickong.auth.refresh-token.*).
 *
 * @param ttl 수명. 재발급할 때마다 '지금 + ttl'로 연장됩니다(기본 14일)
 */
@ConfigurationProperties("pickong.auth.refresh-token")
public record RefreshTokenProperties(Duration ttl) {

    public RefreshTokenProperties {
        if (ttl == null) {
            ttl = Duration.ofDays(14);
        }
    }
}
