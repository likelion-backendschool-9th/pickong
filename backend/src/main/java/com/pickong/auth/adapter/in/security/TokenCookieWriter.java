package com.pickong.auth.adapter.in.security;

import com.pickong.auth.adapter.out.external.jwt.JwtProperties;
import com.pickong.auth.application.dto.IssuedTokens;
import com.pickong.auth.application.service.RefreshTokenProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키를 응답에 쓰고 지웁니다. 로그인(4~5단계) · 재발급 · 로그아웃이 모두 이 클래스를 씁니다.
 * <p>
 * 모든 인증 쿠키의 공통 속성:
 * <ul>
 *   <li>HttpOnly: 프론트 JS가 읽을 수 없음 → XSS로 토큰을 훔쳐 가지 못함</li>
 *   <li>SameSite=Lax: 다른 사이트의 POST · fetch에는 실리지 않음 (CSRF 1차 방어)</li>
 *   <li>Secure: HTTPS에서만 전송 (설정, 로컬만 끔)</li>
 *   <li>Domain: 기본은 지금 도메인만 (설정)</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(AuthCookieProperties.class)
public class TokenCookieWriter {

    private final AuthCookieProperties cookieProperties;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public TokenCookieWriter(AuthCookieProperties cookieProperties, JwtProperties jwtProperties,
            RefreshTokenProperties refreshTokenProperties) {
        this.cookieProperties = cookieProperties;
        this.accessTokenTtl = jwtProperties.accessTokenTtl();
        this.refreshTokenTtl = refreshTokenProperties.ttl();
    }

    /**
     * 발급된 두 토큰을 쿠키로 씁니다.
     * 쿠키 수명(Max-Age)을 토큰 수명과 같게 둬서, 토큰이 만료되면 브라우저도 쿠키를 지웁니다.
     * Refresh 쿠키가 14일 유지되므로 브라우저를 껐다 켜도 로그인 상태가 이어집니다.
     */
    public void write(HttpServletResponse response, IssuedTokens tokens) {
        add(response, cookie(AuthCookies.ACCESS_TOKEN, tokens.accessToken(), AuthCookies.ACCESS_TOKEN_PATH, accessTokenTtl));
        add(response, cookie(AuthCookies.REFRESH_TOKEN, tokens.refreshToken(), AuthCookies.REFRESH_TOKEN_PATH, refreshTokenTtl));
    }

    /**
     * 두 쿠키를 지웁니다. (같은 이름 · 경로 · 도메인으로 Max-Age=0을 보내면 브라우저가 삭제)
     * 로그아웃과 재발급 실패(만료 · 재사용 · 정지) 때 씁니다.
     */
    public void clear(HttpServletResponse response) {
        add(response, cookie(AuthCookies.ACCESS_TOKEN, "", AuthCookies.ACCESS_TOKEN_PATH, Duration.ZERO));
        add(response, cookie(AuthCookies.REFRESH_TOKEN, "", AuthCookies.REFRESH_TOKEN_PATH, Duration.ZERO));
    }

    /** 공통 속성을 붙인 쿠키 하나를 만듭니다. */
    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge);
        if (cookieProperties.domain() != null) {
            builder.domain(cookieProperties.domain());
        }
        return builder.build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        // addHeader: 쿠키 여러 개를 각각의 Set-Cookie 헤더로 보냅니다. (setHeader는 앞의 것을 덮어씀)
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
