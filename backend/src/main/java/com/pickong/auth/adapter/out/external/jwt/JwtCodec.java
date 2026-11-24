package com.pickong.auth.adapter.out.external.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 서명된 토큰(JWT, HS256)을 만들고 읽는 공통 도구.
 * Access Token · 임시 가입 토큰 · OAuth state 쿠키 · 계정 연결 토큰이 모두 이 클래스로 서명합니다.
 * <p>
 * <b>토큰 종류({@link TokenType}) 구분이 핵심입니다.</b> 셋 다 같은 비밀값으로 서명하므로, 종류를 확인하지 않으면
 * 임시 가입 토큰(subject = 카카오 회원번호)이 Access Token(subject = 찜콩 회원 id)으로 통과할 수 있습니다.
 * 그래서 만들 때 {@code token_type}을 넣고, 읽을 때 기대한 종류가 아니면 거절합니다.
 */
@Component
public class JwtCodec {

    /** 발급자(iss). 우리 서버가 만든 토큰인지 확인합니다. */
    static final String ISSUER = "pickong";
    static final String TOKEN_TYPE_CLAIM = "token_type";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    /**
     * 비밀값 하나로 서명용(encoder)과 검증용(decoder)을 함께 준비합니다. HS256은 같은 키로 서명하고 검증합니다.
     *
     * @param properties 비밀값 (application.yml의 pickong.auth.jwt)
     * @param clock      현재 시각. 운영은 실제 시계, 테스트는 고정된 시계를 넣습니다
     */
    public JwtCodec(JwtProperties properties, Clock clock) {
        SecretKey key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // 만료 검증에 앱의 Clock을 써서 테스트에서 시각을 고정할 수 있게 합니다. 서버 간 시계 오차는 기본값(60초)만큼 허용합니다.
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator, new JwtIssuerValidator(ISSUER)));
        this.decoder = nimbusDecoder;
        this.clock = clock;
    }

    /**
     * 토큰을 만듭니다.
     *
     * @param type    토큰 종류 (token_type 클레임으로 들어감)
     * @param subject sub 클레임 (토큰의 주인: 회원 id, 소셜 회원번호 등)
     * @param claims  추가로 담을 값. 누구나 읽을 수 있으므로 비밀은 넣지 않습니다
     * @param ttl     유효 시간
     */
    public String encode(TokenType type, String subject, Map<String, Object> claims, Duration ttl) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(TOKEN_TYPE_CLAIM, type.value());
        claims.forEach(builder::claim);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }

    /**
     * 토큰을 검증하고 읽습니다. 서명 · 만료 · 발급자 · <b>토큰 종류</b>가 모두 맞아야 합니다.
     *
     * @return 검증된 토큰. 하나라도 틀리면(만료 · 위조 · 다른 종류 · 형식 오류) 빈 Optional
     */
    public Optional<Jwt> decode(TokenType expectedType, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            if (!Objects.equals(expectedType.value(), jwt.getClaimAsString(TOKEN_TYPE_CLAIM))) {
                return Optional.empty();
            }
            return Optional.of(jwt);
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    /** 서명 토큰의 종류. 같은 키로 서명해도 서로 바꿔 쓸 수 없게 합니다. */
    public enum TokenType {
        /** Access Token (15분, 쿠키 pickong_at) */
        ACCESS("access"),
        /** 신규 회원의 임시 가입 세션 (30분, 쿠키 pickong_signup) */
        SIGNUP("signup"),
        /** OAuth 로그인 시작 ~ 콜백 사이의 state (10분, 쿠키 pickong_oauth) */
        OAUTH_STATE("oauth_state"),
        /** 로그인한 회원의 계정 연결 시작 (5분, OAuth 시작 주소의 ?link=) */
        LINK("link");

        private final String value;

        TokenType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
