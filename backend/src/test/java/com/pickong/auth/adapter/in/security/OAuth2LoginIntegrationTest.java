package com.pickong.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.out.SignupTokenPort;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.common.security.AccessTokenVerifier;
import com.pickong.common.security.LoginUser;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 소셜 로그인 전체 흐름 통합 테스트: 시작 → (제공자) → 콜백 → 토큰 교환 → 사용자 정보 → 판단 → 쿠키 · 이동.
 * <p>
 * 실제 카카오 대신 테스트 안에서 <b>가짜 카카오 서버</b>({@link FakeKakao})를 띄워 토큰 · 사용자 정보 API를 흉내 냅니다.
 * 동의 화면은 브라우저 단계라 건너뛰고, 시작 응답의 state로 콜백을 직접 호출합니다.
 * (실제 카카오 · Google 인증은 자동 테스트로 검증할 수 없어 PR에 미검증으로 표시합니다)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OAuth2LoginIntegrationTest {

    /** 카카오의 토큰 · 사용자 정보 주소만 가짜 서버로 바꿉니다. */
    @DynamicPropertySource
    static void fakeKakaoEndpoints(DynamicPropertyRegistry registry) {
        FakeKakao.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SignupTokenPort signupTokens;

    @Autowired
    AccessTokenVerifier accessTokenVerifier;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM users");
        FakeKakao.respondWith(FakeKakao.user(4242, "kong@kakao.com", true));
    }

    private long insertUser(String username, String status) {
        return jdbc.queryForObject("INSERT INTO users (username, status) VALUES (?, ?) RETURNING id", Long.class, username, status);
    }

    private void linkSocial(long userId, String provider, String providerId, String email) {
        jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_id, email) VALUES (?, ?, ?, ?)",
                userId, provider, providerId, email);
    }

    // ── 흐름 도우미 ─────────────────────────────────────────

    /** 로그인 시작 응답: 제공자로 가는 주소와 state 쿠키 */
    private record Started(URI location, Cookie stateCookie) {
        /** 주소의 state는 URL 인코딩돼 있어(%3D 등) 브라우저처럼 디코딩해서 씁니다. */
        String state() {
            String raw = UriComponentsBuilder.fromUri(location).build().getQueryParams().getFirst("state");
            return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
        }
    }

    private Started start(String provider) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/oauth2/authorization/" + provider)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        String header = setCookie(result, AuthCookies.OAUTH_STATE);
        String value = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        return new Started(URI.create(result.getResponse().getRedirectedUrl()), new Cookie(AuthCookies.OAUTH_STATE, value));
    }

    /** 제공자가 우리 콜백으로 돌려보낸 것처럼 호출합니다. */
    private MvcResult callback(String provider, Map<String, String> params, Cookie... cookies) throws Exception {
        var request = get("/api/v1/auth/oauth2/callback/" + provider).servletPath("/api/v1");
        params.forEach(request::param);
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult loginWithKakao() throws Exception {
        Started started = start("kakao");
        return callback("kakao", Map.of("code", "fake-code", "state", started.state()), started.stateCookie());
    }

    private static String setCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿠키가 없습니다. 실제: " + result.getResponse().getHeaders("Set-Cookie")));
    }

    private static boolean hasLiveCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(h -> h.startsWith(name + "=") && !h.contains("Max-Age=0"));
    }

    private static String cookieValue(String header) {
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    // ── 시작 ──────────────────────────────────────────────

    @Test
    @DisplayName("카카오 로그인 시작: 카카오 동의 화면으로 302, 콜백 주소는 프론트 기준, state 쿠키 발급, 세션 없음")
    void kakaoStart() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/oauth2/authorization/kakao")).andReturn();

        URI location = URI.create(result.getResponse().getRedirectedUrl());
        Map<String, String> query = Arrays.stream(location.getRawQuery().split("&"))
                .map(p -> p.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> java.net.URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(location.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(query).containsEntry("client_id", "test-kakao-client")
                .containsEntry("redirect_uri", "http://localhost:3000/api/v1/auth/oauth2/callback/kakao")
                .containsEntry("response_type", "code")
                .containsKey("state")
                .as("일반 로그인은 계정 선택 화면을 강제하지 않음").doesNotContainKey("prompt");
        assertThat(query.get("scope")).contains("account_email");
        assertThat(setCookie(result, AuthCookies.OAUTH_STATE)).contains("Path=/api/v1/auth/oauth2", "HttpOnly", "SameSite=Lax", "Max-Age=600");
        assertThat(result.getRequest().getSession(false)).as("세션을 만들지 않음").isNull();
    }

    @Test
    @DisplayName("Google 로그인 시작: Google 동의 화면으로 302, OIDC(openid · nonce)")
    void googleStart() throws Exception {
        Started started = start("google");

        assertThat(started.location().getHost()).isEqualTo("accounts.google.com");
        assertThat(started.location().getRawQuery()).contains("scope=openid", "nonce=",
                "redirect_uri=http://localhost:3000/api/v1/auth/oauth2/callback/google");
    }

    @Test
    @DisplayName("지원하지 않는 제공자로 시작하면 500이 아니라 /login?error=OAUTH_FAILED")
    void unknownProviderStart() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/oauth2/authorization/naver")).andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=OAUTH_FAILED");
    }

    // ── 콜백: 결과별 ───────────────────────────────────────

    @Test
    @DisplayName("신규 회원: /signup으로 이동, 임시 가입 쿠키(30분)에 카카오 계정 정보가 담긴다")
    void newMemberGoesToSignup() throws Exception {
        MvcResult result = loginWithKakao();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/signup");
        String signupCookie = setCookie(result, AuthCookies.SIGNUP_TOKEN);
        assertThat(signupCookie).contains("Path=/api/v1/auth", "Max-Age=1800", "HttpOnly", "SameSite=Lax");
        assertThat(signupTokens.verify(cookieValue(signupCookie))).contains(
                new SocialProfile(SocialProvider.KAKAO, "4242", "kong@kakao.com", "콩이", "https://k.kakaocdn.net/dn/a.jpg"));
        assertThat(hasLiveCookie(result, AuthCookies.ACCESS_TOKEN)).as("가입 전에는 정식 토큰 없음").isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).as("회원은 온보딩 완료 때 생성").isZero();
    }

    @Test
    @DisplayName("기존 회원: /feed로 이동, Access · Refresh 쿠키 발급, Refresh Token이 DB에 저장된다")
    void existingMemberLogsIn() throws Exception {
        long userId = insertUser("seoyeon", "ACTIVE");
        linkSocial(userId, "KAKAO", "4242", "kong@kakao.com");

        MvcResult result = loginWithKakao();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/feed");
        String access = cookieValue(setCookie(result, AuthCookies.ACCESS_TOKEN));
        assertThat(accessTokenVerifier.verify(access)).contains(new LoginUser(userId));
        assertThat(hasLiveCookie(result, AuthCookies.REFRESH_TOKEN)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(setCookie(result, AuthCookies.OAUTH_STATE)).as("state 쿠키는 한 번 쓰고 삭제").contains("Max-Age=0");
    }

    @Test
    @DisplayName("탈퇴 유예 중(30일 안) 회원: 세션은 주지 않고 탈퇴 취소 쿠키만 발급 → /account/restore")
    void withdrawalPendingMemberGoesToRestore() throws Exception {
        long userId = insertUser("seoyeon", "WITHDRAWN_PENDING");
        jdbc.update("UPDATE users SET withdrawal_requested_at = now() - interval '3 days' WHERE id = ?", userId);
        linkSocial(userId, "KAKAO", "4242", "kong@kakao.com");

        MvcResult result = loginWithKakao();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/account/restore");
        assertThat(hasLiveCookie(result, AuthCookies.RESTORE_TOKEN)).isTrue();
        assertThat(hasLiveCookie(result, AuthCookies.ACCESS_TOKEN)).as("탈퇴 중인 계정에 로그인 세션을 만들지 않음").isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, userId)).isZero();
    }

    @Test
    @DisplayName("탈퇴 신청 후 30일이 지난(익명화 전) 회원: 취소할 수 없어 /login?error=ACCOUNT_UNAVAILABLE")
    void withdrawalExpiredMemberUnavailable() throws Exception {
        long userId = insertUser("seoyeon", "WITHDRAWN_PENDING");
        jdbc.update("UPDATE users SET withdrawal_requested_at = now() - interval '31 days' WHERE id = ?", userId);
        linkSocial(userId, "KAKAO", "4242", null);

        MvcResult result = loginWithKakao();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=ACCOUNT_UNAVAILABLE");
        assertThat(hasLiveCookie(result, AuthCookies.RESTORE_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("기존 회원인데 재동의가 필요한 필수 약관이 시행됨: 세션은 발급하고 /terms/consent로 이동")
    void existingMemberWithPendingTermsGoesToConsent() throws Exception {
        long userId = insertUser("seoyeon", "ACTIVE");
        linkSocial(userId, "KAKAO", "4242", "kong@kakao.com");
        jdbc.update("""
                INSERT INTO terms (type, version, title, content_url, is_required, effective_at, requires_reconsent)
                VALUES ('PRIVACY', '9.9-login-test', '개인정보 새 버전', '/terms/privacy', TRUE, now() - interval '1 day', TRUE)
                """);
        try {
            MvcResult result = loginWithKakao();

            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/terms/consent");
            assertThat(hasLiveCookie(result, AuthCookies.ACCESS_TOKEN)).as("동의하러 가야 하므로 로그인은 된 상태").isTrue();
        } finally {
            // 이 테스트만 약관 버전을 넣으므로 바로 지웁니다 (남기면 다른 테스트의 시행 중인 약관이 바뀜)
            jdbc.update("DELETE FROM user_terms_agreements WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM terms WHERE version = '9.9-login-test'");
        }
    }

    @Test
    @DisplayName("정지된 회원: /login?error=ACCOUNT_UNAVAILABLE, 토큰 없음")
    void suspendedMember() throws Exception {
        long userId = insertUser("seoyeon", "SUSPENDED");
        linkSocial(userId, "KAKAO", "4242", null);

        MvcResult result = loginWithKakao();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=ACCOUNT_UNAVAILABLE");
        assertThat(hasLiveCookie(result, AuthCookies.ACCESS_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("이메일이 이미 다른 계정(Google로 가입)에 있으면: /login?error=EMAIL_ALREADY_REGISTERED&provider=google")
    void emailAlreadyRegistered() throws Exception {
        long other = insertUser("jimin", "ACTIVE");
        linkSocial(other, "GOOGLE", "1089", "kong@kakao.com");

        MvcResult result = loginWithKakao();

        assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo("http://localhost:3000/login?error=EMAIL_ALREADY_REGISTERED&provider=google");
        assertThat(hasLiveCookie(result, AuthCookies.SIGNUP_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("카카오가 인증하지 않은 이메일은 중복 확인에 쓰지 않는다 (남의 이메일로 막히지 않음)")
    void unverifiedEmailIgnored() throws Exception {
        long other = insertUser("jimin", "ACTIVE");
        linkSocial(other, "GOOGLE", "1089", "kong@kakao.com");
        FakeKakao.respondWith(FakeKakao.user(4242, "kong@kakao.com", false));

        MvcResult result = loginWithKakao();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/signup");
        assertThat(signupTokens.verify(cookieValue(setCookie(result, AuthCookies.SIGNUP_TOKEN))).orElseThrow().email()).isNull();
    }

    // ── 콜백: 실패 ─────────────────────────────────────────

    @Test
    @DisplayName("state가 다르면 /login?error=OAUTH_FAILED (로그인 CSRF 차단)")
    void stateMismatch() throws Exception {
        Started started = start("kakao");

        MvcResult result = callback("kakao", Map.of("code", "fake-code", "state", "attacker-state"), started.stateCookie());

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=OAUTH_FAILED");
        assertThat(hasLiveCookie(result, AuthCookies.SIGNUP_TOKEN)).isFalse();
        assertThat(hasLiveCookie(result, AuthCookies.ACCESS_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("state 쿠키 없이 온 콜백(다른 브라우저에서 연 링크)은 /login?error=OAUTH_FAILED")
    void callbackWithoutStateCookie() throws Exception {
        Started started = start("kakao");

        MvcResult result = callback("kakao", Map.of("code", "fake-code", "state", started.state()));

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=OAUTH_FAILED");
    }

    @Test
    @DisplayName("동의 화면에서 취소하면 /login?error=OAUTH_CANCELLED")
    void userCancelled() throws Exception {
        Started started = start("kakao");

        MvcResult result = callback("kakao", Map.of("error", "access_denied", "state", started.state()), started.stateCookie());

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=OAUTH_CANCELLED");
    }
}
