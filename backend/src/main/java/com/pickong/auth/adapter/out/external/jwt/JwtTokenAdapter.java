package com.pickong.auth.adapter.out.external.jwt;

import com.pickong.auth.application.port.out.TokenIssuerPort;
import com.pickong.common.security.AccessTokenVerifier;
import com.pickong.common.security.LoginUser;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * Access Token(JWT, HS256) 발급 · 검증. Spring Security의 Nimbus 인코더 · 디코더를 씁니다.
 * <p>
 * 토큰 내용: {@code iss=pickong}, {@code sub=회원 id}, {@code iat}, {@code exp}. 이름 · 이메일 같은 개인정보는 넣지 않습니다.
 * (JWT 내용은 암호화가 아니라 누구나 읽을 수 있는 인코딩이기 때문)
 * <p>
 * 백엔드와 별개인 서버(예: 분리된 WebSocket 서버)가 토큰을 검증하게 되면 비밀값을 나눠 주지 않도록 RS256(공개키 검증)으로 바꿉니다.
 */
@Component
public class JwtTokenAdapter implements TokenIssuerPort, AccessTokenVerifier {

    /** 발급자(iss). 우리 서버가 만든 토큰인지 한 번 더 확인하는 데 씁니다. */
    static final String ISSUER = "pickong";

    /** 토큰 만들기(서명) 담당 */
    private final JwtEncoder encoder;
    /** 토큰 읽기(서명 · 만료 · 발급자 검증) 담당 */
    private final JwtDecoder decoder;
    private final Duration accessTokenTtl;
    private final Clock clock;

    /**
     * 비밀값 하나로 서명용(encoder)과 검증용(decoder)을 함께 준비합니다. HS256은 같은 키로 서명하고 검증합니다.
     *
     * @param properties 비밀값과 만료 시간 (application.yml의 pickong.auth.jwt)
     * @param clock      현재 시각. 운영은 실제 시계, 테스트는 고정된 시계를 넣습니다
     */
    public JwtTokenAdapter(JwtProperties properties, Clock clock) {
        // 비밀값 문자열을 HS256 서명 키로 바꿉니다.
        SecretKey key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // 만료 검증에 앱의 Clock을 써서 테스트에서 시각을 고정할 수 있게 합니다. 서버 간 시계 오차는 기본값(60초)만큼 허용합니다.
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator, new JwtIssuerValidator(ISSUER)));
        this.decoder = nimbusDecoder;

        this.accessTokenTtl = properties.accessTokenTtl();
        this.clock = clock;
    }

    /**
     * 회원의 Access Token을 만듭니다. (로그인 · 가입 완료 · 재발급 때 호출 — 3~4단계)
     * <p>
     * 결과 예: {@code eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJwaWNrb25nIiwic3ViIjoiNDIiLC4uLn0.서명}
     * — 가운데 부분에 iss=pickong, sub=42(회원 id), iat=발급 시각, exp=발급 시각+15분이 들어갑니다.
     */
    @Override
    public String issueAccessToken(Long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * 쿠키로 받은 토큰이 진짜이고 아직 유효한지 확인합니다. (매 요청마다 인증 필터가 호출)
     * <p>
     * decoder가 확인하는 것: ① 우리 비밀값으로 서명됐는지(위조 아님) ② 만료되지 않았는지 ③ 발급자가 pickong인지.
     *
     * @return 모두 통과하면 토큰의 회원 id로 만든 LoginUser, 하나라도 실패하면 빈 Optional
     */
    @Override
    public Optional<LoginUser> verify(String accessToken) {
        try {
            Jwt jwt = decoder.decode(accessToken);
            return Optional.of(new LoginUser(Long.valueOf(jwt.getSubject())));
        } catch (JwtException | NumberFormatException e) {
            // 만료 · 서명 불일치 · 형식 오류는 모두 '인증 안 됨'으로 봅니다. 원인은 응답에 드러내지 않습니다.
            return Optional.empty();
        }
    }
}
