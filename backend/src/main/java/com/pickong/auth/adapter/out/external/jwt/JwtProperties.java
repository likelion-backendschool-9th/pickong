package com.pickong.auth.adapter.out.external.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 (pickong.auth.jwt.*).
 *
 * @param secret         HS256 서명 비밀값. 환경변수 JWT_SECRET 또는 backend/.env에서 받습니다. 32바이트 이상
 * @param accessTokenTtl Access Token 만료 시간 (기본 15분)
 */
@ConfigurationProperties("pickong.auth.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl) {

    /** HS256은 256비트(32바이트) 이상의 키가 필요합니다. */
    static final int MIN_SECRET_BYTES = 32;

    /**
     * 앱이 시작할 때 설정값을 읽으면서 바로 검사합니다.
     * 비밀값이 비어 있거나 짧으면 예외를 던져 앱이 뜨지 않게 하고, 만료 시간이 없으면 15분으로 채웁니다.
     */
    public JwtProperties {
        // 기본값을 두지 않고, 비어 있거나 짧으면 앱이 시작하지 않게 합니다. 운영에 약한 키가 들어가는 것을 막기 위해서입니다.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET이 없거나 너무 짧습니다. 환경변수 또는 backend/.env에 32바이트 이상 값을 넣어 주세요. "
                            + "(예: openssl rand -base64 32)");
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
    }
}
