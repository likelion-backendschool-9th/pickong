package com.pickong.auth.adapter.out.external.jwt;

import com.pickong.auth.adapter.out.external.jwt.JwtCodec.TokenType;
import com.pickong.auth.application.port.out.TokenIssuerPort;
import com.pickong.common.security.AccessTokenVerifier;
import com.pickong.common.security.LoginUser;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Access Token(JWT, HS256) 발급 · 검증. 서명은 {@link JwtCodec}이 하고, 이 클래스는 Access Token 규칙만 담당합니다.
 * <p>
 * 토큰 내용: {@code iss=pickong}, {@code sub=회원 id}, {@code token_type=access}, {@code iat}, {@code exp}.
 * 이름 · 이메일 같은 개인정보는 넣지 않습니다. (JWT 내용은 암호화가 아니라 누구나 읽을 수 있는 인코딩이기 때문)
 * <p>
 * 백엔드와 별개인 서버(예: 분리된 WebSocket 서버)가 토큰을 검증하게 되면 비밀값을 나눠 주지 않도록 RS256(공개키 검증)으로 바꿉니다.
 */
@Component
public class JwtTokenAdapter implements TokenIssuerPort, AccessTokenVerifier {

    private final JwtCodec codec;
    private final Duration accessTokenTtl;

    /**
     * @param codec      서명 도구 (비밀값 · 시계를 가짐)
     * @param properties Access Token 만료 시간 (application.yml의 pickong.auth.jwt.access-token-ttl)
     */
    public JwtTokenAdapter(JwtCodec codec, JwtProperties properties) {
        this.codec = codec;
        this.accessTokenTtl = properties.accessTokenTtl();
    }

    /**
     * 회원의 Access Token을 만듭니다. (로그인 · 가입 완료 · 재발급 때 호출)
     * <p>
     * 결과 예: {@code eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJwaWNrb25nIiwic3ViIjoiNDIiLC4uLn0.서명}
     * — 가운데 부분에 iss=pickong, sub=42(회원 id), token_type=access, iat=발급 시각, exp=발급 시각+15분이 들어갑니다.
     */
    @Override
    public String issueAccessToken(Long userId) {
        return codec.encode(TokenType.ACCESS, String.valueOf(userId), Map.of(), accessTokenTtl);
    }

    /**
     * 쿠키로 받은 토큰이 진짜 Access Token이고 아직 유효한지 확인합니다. (매 요청마다 인증 필터가 호출)
     * <p>
     * 확인하는 것: ① 우리 비밀값으로 서명됐는지(위조 아님) ② 만료되지 않았는지 ③ 발급자가 pickong인지
     * ④ 토큰 종류가 access인지 (임시 가입 토큰 · state 토큰은 거절)
     *
     * @return 모두 통과하면 토큰의 회원 id로 만든 LoginUser, 하나라도 실패하면 빈 Optional
     */
    @Override
    public Optional<LoginUser> verify(String accessToken) {
        try {
            return codec.decode(TokenType.ACCESS, accessToken)
                    .map(jwt -> new LoginUser(Long.valueOf(jwt.getSubject())));
        } catch (NumberFormatException e) {
            // subject가 숫자가 아닌 토큰은 우리가 만든 Access Token이 아닙니다.
            return Optional.empty();
        }
    }
}
