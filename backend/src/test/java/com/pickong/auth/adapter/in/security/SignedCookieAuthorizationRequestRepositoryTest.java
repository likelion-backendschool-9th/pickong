package com.pickong.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.auth.adapter.out.external.jwt.JwtCodec;
import com.pickong.auth.adapter.out.external.jwt.JwtProperties;
import com.pickong.auth.application.service.RefreshTokenProperties;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** OAuth state 쿠키 저장소 단위 테스트. */
class SignedCookieAuthorizationRequestRepositoryTest {

    private static final JwtProperties JWT = new JwtProperties("test-only-jwt-secret-0123456789-abcdefghij", Duration.ofMinutes(15));
    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private static SignedCookieAuthorizationRequestRepository repositoryAt(Instant now) {
        TokenCookieWriter cookieWriter = new TokenCookieWriter(new AuthCookieProperties(true, null), JWT, new RefreshTokenProperties(null));
        return new SignedCookieAuthorizationRequestRepository(new JwtCodec(JWT, Clock.fixed(now, ZoneOffset.UTC)), cookieWriter);
    }

    /** Google(OIDC)처럼 nonce가 attributes · additionalParameters에 들어 있는 요청 */
    private static OAuth2AuthorizationRequest googleRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id")
                .redirectUri("http://localhost:3000/api/v1/auth/oauth2/callback/google")
                .scopes(Set.of("openid", "email", "profile"))
                .state("state-abc")
                .additionalParameters(Map.of("nonce", "hashed-nonce"))
                .attributes(Map.of("registration_id", "google", "nonce", "raw-nonce"))
                .authorizationRequestUri("https://accounts.google.com/o/oauth2/v2/auth?client_id=client-id&state=state-abc")
                .build();
    }

    /** save가 쓴 Set-Cookie에서 pickong_oauth 값을 꺼냅니다. */
    private static String savedCookie(MockHttpServletResponse response) {
        String header = response.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith("pickong_oauth=")).findFirst().orElseThrow();
        return header.substring("pickong_oauth=".length(), header.indexOf(';'));
    }

    private static MockHttpServletRequest callback(String cookie, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/oauth2/callback/google");
        if (cookie != null) {
            request.setCookies(new Cookie("pickong_oauth", cookie));
        }
        request.setParameter("state", state);
        return request;
    }

    @Test
    @DisplayName("저장한 시작 정보를 같은 state의 콜백에서 그대로 되찾는다 (Google nonce 포함)")
    void roundTrip() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repositoryAt(NOW).saveAuthorizationRequest(googleRequest(), new MockHttpServletRequest(), saveResponse);
        assertThat(saveResponse.getHeader("Set-Cookie")).contains("Path=/api/v1/auth/oauth2", "Max-Age=600", "HttpOnly", "SameSite=Lax");

        OAuth2AuthorizationRequest loaded = repositoryAt(NOW).loadAuthorizationRequest(callback(savedCookie(saveResponse), "state-abc"));

        OAuth2AuthorizationRequest expected = googleRequest();
        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo(expected.getState());
        assertThat(loaded.getClientId()).isEqualTo(expected.getClientId());
        assertThat(loaded.getRedirectUri()).isEqualTo(expected.getRedirectUri());
        assertThat(loaded.getScopes()).isEqualTo(expected.getScopes());
        assertThat(loaded.getAttributes()).isEqualTo(expected.getAttributes());
        assertThat(loaded.getAdditionalParameters()).isEqualTo(expected.getAdditionalParameters());
        assertThat(loaded.getAuthorizationRequestUri()).isEqualTo(expected.getAuthorizationRequestUri());
    }

    @Test
    @DisplayName("콜백의 state가 쿠키의 state와 다르면 null (로그인 CSRF 차단)")
    void stateMismatch() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repositoryAt(NOW).saveAuthorizationRequest(googleRequest(), new MockHttpServletRequest(), saveResponse);

        assertThat(repositoryAt(NOW).loadAuthorizationRequest(callback(savedCookie(saveResponse), "attacker-state"))).isNull();
    }

    @Test
    @DisplayName("쿠키가 없으면(다른 브라우저에서 연 콜백) null")
    void noCookie() {
        assertThat(repositoryAt(NOW).loadAuthorizationRequest(callback(null, "state-abc"))).isNull();
    }

    @Test
    @DisplayName("쿠키 내용을 바꾸면(서명 불일치) null")
    void tamperedCookie() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repositoryAt(NOW).saveAuthorizationRequest(googleRequest(), new MockHttpServletRequest(), saveResponse);
        String cookie = savedCookie(saveResponse);
        String tampered = cookie.substring(0, cookie.length() - 4) + "AAAA";

        assertThat(repositoryAt(NOW).loadAuthorizationRequest(callback(tampered, "state-abc"))).isNull();
    }

    @Test
    @DisplayName("10분이 지나면 null")
    void expired() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repositoryAt(NOW).saveAuthorizationRequest(googleRequest(), new MockHttpServletRequest(), saveResponse);

        assertThat(repositoryAt(NOW.plus(Duration.ofMinutes(12))).loadAuthorizationRequest(callback(savedCookie(saveResponse), "state-abc"))).isNull();
    }

    @Test
    @DisplayName("콜백에서 꺼내면 쿠키를 지운다 (같은 state 재사용 방지)")
    void removeClearsCookie() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repositoryAt(NOW).saveAuthorizationRequest(googleRequest(), new MockHttpServletRequest(), saveResponse);
        MockHttpServletResponse callbackResponse = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed = repositoryAt(NOW).removeAuthorizationRequest(callback(savedCookie(saveResponse), "state-abc"), callbackResponse);

        assertThat(removed).isNotNull();
        assertThat(callbackResponse.getHeader("Set-Cookie")).startsWith("pickong_oauth=;").contains("Max-Age=0");
    }
}
