package com.pickong.auth.application.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh Token 설정 (pickong.auth.refresh-token.*).
 *
 * @param ttl              수명. 재발급할 때마다 '지금 + ttl'로 연장됩니다(기본 14일)
 * @param cleanupRetention 만료된 뒤 이만큼 지난 토큰 행을 정리 작업이 지웁니다(기본 7일).
 *                         만료 전에는 지우지 않으므로, 폐기된 토큰의 재사용 감지는 만료될 때까지 그대로 동작합니다
 */
@ConfigurationProperties("pickong.auth.refresh-token")
public record RefreshTokenProperties(Duration ttl, Duration cleanupRetention) {

    public RefreshTokenProperties {
        if (ttl == null) {
            ttl = Duration.ofDays(14);
        }
        if (cleanupRetention == null) {
            cleanupRetention = Duration.ofDays(7);
        }
    }
}
