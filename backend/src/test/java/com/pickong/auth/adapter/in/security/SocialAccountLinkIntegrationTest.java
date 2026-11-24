package com.pickong.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.auth.application.port.out.LinkTokenPort;
import com.pickong.auth.domain.vo.SocialProvider;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로그인 수단 관리 통합 테스트: 목록 · 연결 시작 → (제공자) → 콜백 → 연결 · 해제.
 * 실제 PostgreSQL · 보안 필터 · 쿠키를 그대로 거치고, 카카오는 {@link FakeKakao}로 흉내 냅니다.
 * (실제 카카오 · Google의 계정 선택 화면은 자동 테스트로 확인할 수 없어 미검증)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SocialAccountLinkIntegrationTest {

    @DynamicPropertySource
    static void fakeKakaoEndpoints(DynamicPropertyRegistry registry) {
        FakeKakao.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenUseCase tokenUseCase;

    @Autowired
    LinkTokenPort linkTokens;

    private final JsonMapper json = JsonMapper.builder().build();

    /** Google로 가입한 회원 (가입 수단 = Google) */
    private long me;
    private Cookie myAccessCookie;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
        me = insertUser("seoyeon");
        linkSocial(me, "GOOGLE", "1089", "kong@gmail.com");
        myAccessCookie = accessCookie(me);
        FakeKakao.respondWith(FakeKakao.user(4242, "kong@kakao.com", true));
    }

    private long insertUser(String username) {
        return jdbc.queryForObject("INSERT INTO users (username) VALUES (?) RETURNING id", Long.class, username);
    }

    private void linkSocial(long userId, String provider, String providerId, String email) {
        jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_id, email) VALUES (?, ?, ?, ?)",
                userId, provider, providerId, email);
    }

    private Cookie accessCookie(long userId) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenUseCase.issue(userId).accessToken());
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private int countLinks(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM user_social_accounts WHERE user_id = ?", Integer.class, userId);
    }

    // ── 흐름 도우미 ─────────────────────────────────────────

    /** POST /me/social-accounts/kakao → 연결 시작 주소 */
    private String startLink(Cookie accessCookie) throws Exception {
        MvcResult result = mvc.perform(api(post("/api/v1/me/social-accounts/kakao")).with(csrf()).cookie(accessCookie))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("authorizationUrl").asString();
    }

    /** 브라우저처럼 연결 시작 주소로 이동합니다. 쿠키는 주어진 것만 실립니다. */
    private MvcResult openAuthorization(String authorizationUrl, Cookie... cookies) throws Exception {
        var request = get(authorizationUrl);
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mvc.perform(request).andReturn();
    }

    private static Cookie stateCookie(MvcResult started) {
        String header = started.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(AuthCookies.OAUTH_STATE + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("state 쿠키가 없습니다"));
        return new Cookie(AuthCookies.OAUTH_STATE, header.substring(header.indexOf('=') + 1, header.indexOf(';')));
    }

    private static Map<String, String> query(MvcResult started) {
        URI location = URI.create(started.getResponse().getRedirectedUrl());
        return UriComponentsBuilder.fromUri(location).build().getQueryParams().toSingleValueMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> URLDecoder.decode(e.getValue(), StandardCharsets.UTF_8)));
    }

    /** 제공자가 우리 콜백으로 돌려보낸 것처럼 호출합니다. */
    private MvcResult callback(MvcResult started, Map<String, String> params) throws Exception {
        var request = api(get("/api/v1/auth/oauth2/callback/kakao")).cookie(stateCookie(started));
        params.forEach(request::param);
        return mvc.perform(request).andReturn();
    }

    /** 연결 시작부터 콜백까지 한 번에. 카카오는 지금 FakeKakao에 설정된 계정을 돌려줍니다. */
    private MvcResult linkKakao() throws Exception {
        MvcResult started = openAuthorization(startLink(myAccessCookie), myAccessCookie);
        return callback(started, Map.of("code", "fake-code", "state", query(started).get("state")));
    }

    private static boolean hasLiveCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(h -> h.startsWith(name + "=") && !h.contains("Max-Age=0"));
    }

    // ── 목록 ───────────────────────────────────────────────

    @Test
    @DisplayName("목록: 연결 순서대로, 가입 수단은 canUnlink=false")
    void list() throws Exception {
        linkSocial(me, "KAKAO", "4242", null);

        mvc.perform(api(get("/api/v1/me/social-accounts")).cookie(myAccessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].provider").value("GOOGLE"))
                .andExpect(jsonPath("$[0].email").value("kong@gmail.com"))
                .andExpect(jsonPath("$[0].canUnlink").value(false))
                .andExpect(jsonPath("$[0].connectedAt").exists())
                .andExpect(jsonPath("$[1].provider").value("KAKAO"))
                .andExpect(jsonPath("$[1].email").doesNotExist())
                .andExpect(jsonPath("$[1].canUnlink").value(true));
    }

    @Test
    @DisplayName("목록: 로그인하지 않으면 401")
    void listRequiresLogin() throws Exception {
        mvc.perform(api(get("/api/v1/me/social-accounts"))).andExpect(status().isUnauthorized());
    }

    // ── 연결 시작 ───────────────────────────────────────────

    @Test
    @DisplayName("연결 시작: 연결 토큰이 붙은 같은 출처 주소를 돌려준다")
    void startReturnsAuthorizationUrl() throws Exception {
        String url = startLink(myAccessCookie);

        assertThat(url).startsWith("/api/v1/auth/oauth2/authorization/kakao?link=");
        String token = url.substring(url.indexOf("link=") + 5);
        assertThat(linkTokens.verify(token, SocialProvider.KAKAO)).contains(me);
    }

    @Test
    @DisplayName("연결 시작: CSRF 헤더가 없으면 403 (다른 사이트가 연결을 시작시킬 수 없음)")
    void startRequiresCsrf() throws Exception {
        mvc.perform(api(post("/api/v1/me/social-accounts/kakao")).cookie(myAccessCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
    }

    @Test
    @DisplayName("연결 시작: 이미 연결된 제공자면 409 PROVIDER_ALREADY_LINKED, 모르는 제공자면 400")
    void startRejected() throws Exception {
        mvc.perform(api(post("/api/v1/me/social-accounts/google")).with(csrf()).cookie(myAccessCookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROVIDER_ALREADY_LINKED"));
        mvc.perform(api(post("/api/v1/me/social-accounts/naver")).with(csrf()).cookie(myAccessCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("연결 시작 주소로 이동: 카카오로 302, 계정 선택 화면(prompt=select_account), state 쿠키 발급")
    void authorizationForLink() throws Exception {
        MvcResult started = openAuthorization(startLink(myAccessCookie), myAccessCookie);

        assertThat(started.getResponse().getStatus()).isEqualTo(302);
        assertThat(URI.create(started.getResponse().getRedirectedUrl()).getHost()).isEqualTo("kauth.kakao.com");
        assertThat(query(started)).containsEntry("prompt", "select_account").containsKey("state");
        assertThat(stateCookie(started).getValue()).isNotBlank();
    }

    @Test
    @DisplayName("연결 시작 주소만 있고 로그인 쿠키가 없으면 /settings/login-methods?error=LINK_EXPIRED")
    void authorizationWithoutLogin() throws Exception {
        MvcResult result = openAuthorization(startLink(myAccessCookie));

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/settings/login-methods?error=LINK_EXPIRED");
        assertThat(hasLiveCookie(result, AuthCookies.OAUTH_STATE)).isFalse();
    }

    @Test
    @DisplayName("새어 나간 연결 주소를 다른 회원이 자기 브라우저에서 열면 LINK_EXPIRED")
    void authorizationByOtherUser() throws Exception {
        long other = insertUser("jimin");

        MvcResult result = openAuthorization(startLink(myAccessCookie), accessCookie(other));

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/settings/login-methods?error=LINK_EXPIRED");
    }

    @Test
    @DisplayName("연결 토큰이 위조됐거나 다른 제공자용이면 LINK_EXPIRED")
    void authorizationWithBadToken() throws Exception {
        String googleToken = linkTokens.issue(me, SocialProvider.GOOGLE);

        assertThat(openAuthorization("/api/v1/auth/oauth2/authorization/kakao?link=" + googleToken, myAccessCookie)
                .getResponse().getRedirectedUrl()).endsWith("/settings/login-methods?error=LINK_EXPIRED");
        assertThat(openAuthorization("/api/v1/auth/oauth2/authorization/kakao?link=forged", myAccessCookie)
                .getResponse().getRedirectedUrl()).endsWith("/settings/login-methods?error=LINK_EXPIRED");
    }

    // ── 연결 콜백 ───────────────────────────────────────────

    @Test
    @DisplayName("연결 성공: /settings/login-methods?linked=kakao, 카카오 계정이 내 계정에 추가되고 세션 쿠키는 그대로")
    void linkSucceeds() throws Exception {
        MvcResult result = linkKakao();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/settings/login-methods?linked=kakao");
        assertThat(jdbc.queryForObject("SELECT email FROM user_social_accounts WHERE user_id = ? AND provider = 'KAKAO'",
                String.class, me)).isEqualTo("kong@kakao.com");
        assertThat(hasLiveCookie(result, AuthCookies.ACCESS_TOKEN)).as("새 세션을 만들지 않음").isFalse();
        assertThat(hasLiveCookie(result, AuthCookies.SIGNUP_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("연결한 카카오로 로그인하면 같은 찜콩 계정으로 들어온다")
    void linkedAccountLogsIn() throws Exception {
        linkKakao();

        MvcResult login = mvc.perform(get("/api/v1/auth/oauth2/authorization/kakao")).andReturn();
        MvcResult result = callback(login, Map.of("code", "fake-code", "state", query(login).get("state")));

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/feed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, me)).isEqualTo(2);
    }

    @Test
    @DisplayName("이 카카오 계정이 다른 회원에 연결돼 있으면 ?error=SOCIAL_ACCOUNT_ALREADY_LINKED")
    void linkOwnedByOther() throws Exception {
        long other = insertUser("jimin");
        linkSocial(other, "KAKAO", "4242", null);

        MvcResult result = linkKakao();

        assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo("http://localhost:3000/settings/login-methods?error=SOCIAL_ACCOUNT_ALREADY_LINKED");
        assertThat(countLinks(me)).isEqualTo(1);
    }

    @Test
    @DisplayName("카카오의 인증된 이메일이 다른 회원 것이면 ?error=EMAIL_ALREADY_REGISTERED (1인 1계정)")
    void linkEmailOfOther() throws Exception {
        long other = insertUser("jimin");
        linkSocial(other, "GOOGLE", "2077", "kong@kakao.com");

        MvcResult result = linkKakao();

        assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo("http://localhost:3000/settings/login-methods?error=EMAIL_ALREADY_REGISTERED");
        assertThat(countLinks(me)).isEqualTo(1);
    }

    @Test
    @DisplayName("동의 화면에서 취소하면 /settings/login-methods?error=OAUTH_CANCELLED (로그인 화면이 아니라)")
    void linkCancelled() throws Exception {
        MvcResult started = openAuthorization(startLink(myAccessCookie), myAccessCookie);

        MvcResult result = callback(started, Map.of("error", "access_denied", "state", query(started).get("state")));

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/settings/login-methods?error=OAUTH_CANCELLED");
    }

    // ── 해제 ───────────────────────────────────────────────

    @Test
    @DisplayName("해제: 나중에 연결한 카카오는 204로 해제되고, 그 뒤 카카오 로그인은 신규 가입으로 간다")
    void unlinkLaterMethod() throws Exception {
        linkSocial(me, "KAKAO", "4242", "kong@kakao.com");

        mvc.perform(api(delete("/api/v1/me/social-accounts/kakao")).with(csrf()).cookie(myAccessCookie))
                .andExpect(status().isNoContent());

        assertThat(countLinks(me)).isEqualTo(1);
        MvcResult login = mvc.perform(get("/api/v1/auth/oauth2/authorization/kakao")).andReturn();
        MvcResult result = callback(login, Map.of("code", "fake-code", "state", query(login).get("state")));
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/signup");
    }

    @Test
    @DisplayName("해제: 가입 수단(Google)은 다른 수단이 있어도 409 SIGNUP_METHOD_CANNOT_BE_UNLINKED")
    void unlinkSignupMethod() throws Exception {
        linkSocial(me, "KAKAO", "4242", null);

        mvc.perform(api(delete("/api/v1/me/social-accounts/google")).with(csrf()).cookie(myAccessCookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNUP_METHOD_CANNOT_BE_UNLINKED"));
        assertThat(countLinks(me)).isEqualTo(2);
    }

    @Test
    @DisplayName("해제: 연결돼 있지 않으면 404 SOCIAL_ACCOUNT_NOT_LINKED, CSRF 헤더가 없으면 403")
    void unlinkRejected() throws Exception {
        mvc.perform(api(delete("/api/v1/me/social-accounts/kakao")).with(csrf()).cookie(myAccessCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_NOT_LINKED"));
        mvc.perform(api(delete("/api/v1/me/social-accounts/google")).cookie(myAccessCookie))
                .andExpect(status().isForbidden());
    }
}
