package com.pickong.auth.adapter.out.external.jwt;

import com.pickong.auth.adapter.out.external.jwt.JwtCodec.TokenType;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.out.SignupTokenPort;
import com.pickong.auth.domain.vo.SocialProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 임시 가입 토큰(JWT, token_type=signup, 30분). 소셜 계정 정보를 서명해서 쿠키로 들고 다니게 해,
 * 서버에 가입 세션 테이블을 두지 않고도 온보딩이 끝날 때까지 기억합니다.
 * <p>
 * 담는 값: sub=소셜 회원번호, provider, email(인증된 것만), name, picture.
 * 이메일 · 이름이 들어가지만 HttpOnly 쿠키(Path=/api/v1/auth)라 프론트 JS나 다른 API 요청에는 노출되지 않습니다.
 */
@Component
public class SignupTokenAdapter implements SignupTokenPort {

    private final JwtCodec codec;

    public SignupTokenAdapter(JwtCodec codec) {
        this.codec = codec;
    }

    /** 소셜 계정 정보를 담아 서명합니다. 값이 없는 항목(null)은 넣지 않습니다. */
    @Override
    public String issue(SocialProfile profile) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("provider", profile.provider().name());
        putIfPresent(claims, "email", profile.email());
        putIfPresent(claims, "name", profile.name());
        putIfPresent(claims, "picture", profile.profileImageUrl());
        return codec.encode(TokenType.SIGNUP, profile.providerUserId(), claims, TTL);
    }

    /** 서명 · 만료 · 종류(signup)를 확인하고 소셜 계정 정보로 되돌립니다. */
    @Override
    public Optional<SocialProfile> verify(String signupToken) {
        return codec.decode(TokenType.SIGNUP, signupToken).flatMap(SignupTokenAdapter::toProfile);
    }

    private static Optional<SocialProfile> toProfile(Jwt jwt) {
        try {
            SocialProvider provider = SocialProvider.valueOf(jwt.getClaimAsString("provider"));
            return Optional.of(new SocialProfile(provider, jwt.getSubject(),
                    jwt.getClaimAsString("email"), jwt.getClaimAsString("name"), jwt.getClaimAsString("picture")));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static void putIfPresent(Map<String, Object> claims, String key, String value) {
        if (value != null && !value.isBlank()) {
            claims.put(key, value);
        }
    }
}
