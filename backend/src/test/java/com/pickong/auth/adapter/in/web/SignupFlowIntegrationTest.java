package com.pickong.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.out.SignupTokenPort;
import com.pickong.auth.application.port.out.TokenIssuerPort;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.common.security.AccessTokenVerifier;
import com.pickong.common.security.LoginUser;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 온보딩(가입 완료) 통합 테스트. 실제 PostgreSQL(V1 + V2 약관 데이터) · 보안 필터 · 쿠키를 그대로 거칩니다.
 * 임시 가입 쿠키는 4단계의 소셜 로그인 대신 {@link SignupTokenPort}로 직접 만들어 씁니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupFlowIntegrationTest {

    private static final SocialProfile KAKAO = new SocialProfile(SocialProvider.KAKAO, "4242", "kong@kakao.com", "콩이",
            "https://k.kakaocdn.net/dn/a.jpg");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SignupTokenPort signupTokens;

    @Autowired
    TokenIssuerPort tokenIssuer;

    @Autowired
    AccessTokenVerifier accessTokenVerifier;

    @BeforeEach
    void setUp() {
        // 외래 키 순서대로 지웁니다. (같은 DB를 쓰는 다른 테스트 클래스와 섞이지 않게)
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private Cookie signupCookie(SocialProfile profile) {
        return new Cookie(AuthCookies.SIGNUP_TOKEN, signupTokens.issue(profile));
    }

    private static String body(String username, String name, boolean useSocialImage, List<String> agreed) {
        String terms = String.join(",", agreed.stream().map(t -> "\"" + t + "\"").toList());
        String nameJson = name == null ? "null" : "\"" + name + "\"";
        return """
                {"username": "%s", "name": %s, "useSocialProfileImage": %s, "agreedTerms": [%s]}
                """.formatted(username, nameJson, useSocialImage, terms);
    }

    private ResultActions signUp(String body, Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = api(post("/api/v1/auth/signup")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mvc.perform(request);
    }

    private long insertUser(String username, String email) {
        return jdbc.queryForObject("INSERT INTO users (username, email) VALUES (?, ?) RETURNING id", Long.class, username, email);
    }

    private static String setCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿠키가 없습니다"));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ── 약관 데이터 (V2 · V12) ─────────────────────────────

    @Test
    @DisplayName("V2 마이그레이션으로 약관 3개(필수 2 · 선택 1, 1.0)가 들어 있다")
    void termsSeeded() {
        List<Map<String, Object>> terms = jdbc.queryForList("SELECT type, version, is_required FROM terms WHERE version = '1.0' ORDER BY id");

        assertThat(terms).hasSize(3);
        assertThat(terms).extracting(t -> t.get("type")).containsExactly("SERVICE", "PRIVACY", "MARKETING");
        assertThat(terms).extracting(t -> t.get("is_required")).containsExactly(true, true, false);
    }

    @Test
    @DisplayName("V12 마이그레이션으로 서비스 이용약관 · 개인정보 1.1이 들어 있다 (필수 그대로, 재동의 필요 없음, 마케팅은 1.0 그대로)")
    void termsV11Seeded() {
        List<Map<String, Object>> terms = jdbc.queryForList(
                "SELECT type, is_required, requires_reconsent FROM terms WHERE version = '1.1' ORDER BY id");

        assertThat(terms).extracting(t -> t.get("type")).containsExactly("SERVICE", "PRIVACY");
        assertThat(terms).extracting(t -> t.get("is_required")).containsExactly(true, true);
        assertThat(terms).extracting(t -> t.get("requires_reconsent")).containsExactly(false, false);
    }

    // ── GET /auth/signup ──────────────────────────────────

    @Test
    @DisplayName("가입 세션 조회: 온보딩 화면 기본값(제공자 · 이름 · 사진 · 이메일)을 준다")
    void getSession() throws Exception {
        mvc.perform(api(get("/api/v1/auth/signup")).cookie(signupCookie(KAKAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("KAKAO"))
                .andExpect(jsonPath("$.name").value("콩이"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://k.kakaocdn.net/dn/a.jpg"))
                .andExpect(jsonPath("$.email").value("kong@kakao.com"))
                .andExpect(jsonPath("$.providerUserId").doesNotExist());
    }

    @Test
    @DisplayName("가입 쿠키가 없으면 401 SIGNUP_SESSION_EXPIRED, 이미 로그인한 상태면 409 ALREADY_REGISTERED")
    void getSessionWithoutCookie() throws Exception {
        mvc.perform(api(get("/api/v1/auth/signup")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_SESSION_EXPIRED"));

        long userId = insertUser("seoyeon", null);
        mvc.perform(api(get("/api/v1/auth/signup")).cookie(new Cookie(AuthCookies.ACCESS_TOKEN, tokenIssuer.issueAccessToken(userId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("위조된 가입 쿠키는 401이고 쿠키를 지운다")
    void getSessionWithBrokenCookie() throws Exception {
        MvcResult result = mvc.perform(api(get("/api/v1/auth/signup")).cookie(new Cookie(AuthCookies.SIGNUP_TOKEN, "broken")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(setCookie(result, AuthCookies.SIGNUP_TOKEN)).contains("Max-Age=0");
    }

    // ── GET /auth/username-availability ───────────────────

    @Test
    @DisplayName("아이디 중복 확인: 사용 중(대소문자 무시) · 예약어는 false, 비어 있으면 true, 형식 오류는 400")
    void usernameAvailability() throws Exception {
        insertUser("seoyeon", null);

        mvc.perform(api(get("/api/v1/auth/username-availability")).param("username", "SeoYeon"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(api(get("/api/v1/auth/username-availability")).param("username", "admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(api(get("/api/v1/auth/username-availability")).param("username", "kong_01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mvc.perform(api(get("/api/v1/auth/username-availability")).param("username", "서연"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_USERNAME"));
    }

    // ── POST /auth/signup: 성공 ────────────────────────────

    @Test
    @DisplayName("가입 완료: 회원 · 소셜 계정 · 약관 동의가 저장되고 세션 쿠키 발급 · 가입 쿠키 삭제")
    void signUpSucceeds() throws Exception {
        MvcResult result = signUp(body("Kong_01", "콩이", true, List.of("SERVICE", "PRIVACY", "MARKETING")), signupCookie(KAKAO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kong_01"))
                .andReturn();

        Map<String, Object> user = jdbc.queryForMap("SELECT id, username, display_name, email, profile_image_url, status FROM users");
        long userId = ((Number) user.get("id")).longValue();
        assertThat(user).containsEntry("username", "kong_01").containsEntry("display_name", "콩이")
                .containsEntry("email", "kong@kakao.com").containsEntry("profile_image_url", "https://k.kakaocdn.net/dn/a.jpg")
                .containsEntry("status", "ACTIVE");
        assertThat(count("SELECT count(*) FROM user_social_accounts WHERE user_id = ? AND provider = 'KAKAO' AND provider_id = '4242' AND email = 'kong@kakao.com'", userId))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT t.type || ' ' || t.version FROM user_terms_agreements a JOIN terms t ON t.id = a.terms_id WHERE a.user_id = ?",
                String.class, userId))
                .as("종류마다 지금 시행 중인 버전으로 기록")
                .containsExactlyInAnyOrder("SERVICE 1.1", "PRIVACY 1.1", "MARKETING 1.0");

        String access = setCookie(result, AuthCookies.ACCESS_TOKEN);
        assertThat(accessTokenVerifier.verify(access.substring(access.indexOf('=') + 1, access.indexOf(';')))).contains(new LoginUser(userId));
        assertThat(setCookie(result, AuthCookies.REFRESH_TOKEN)).doesNotContain("Max-Age=0");
        assertThat(setCookie(result, AuthCookies.SIGNUP_TOKEN)).contains("Max-Age=0");
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE user_id = ?", userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("이름을 비우고 소셜 사진을 안 쓰면 이름 · 사진 없이, 선택 약관 미동의는 기록하지 않는다 (건너뛰기)")
    void signUpWithoutOptionalFields() throws Exception {
        signUp(body("kong_01", null, false, List.of("SERVICE", "PRIVACY")), signupCookie(KAKAO)).andExpect(status().isOk());

        Map<String, Object> user = jdbc.queryForMap("SELECT display_name, profile_image_url FROM users");
        assertThat(user.get("display_name")).isNull();
        assertThat(user.get("profile_image_url")).isNull();
        assertThat(count("SELECT count(*) FROM user_terms_agreements")).isEqualTo(2);
    }

    // ── POST /auth/signup: 실패 ────────────────────────────

    @Test
    @DisplayName("필수 약관 미동의: 400 REQUIRED_TERMS_NOT_AGREED이고 회원 · 소셜 계정이 남지 않는다 (한 트랜잭션)")
    void requiredTermsMissing() throws Exception {
        signUp(body("kong_01", "콩이", true, List.of("SERVICE", "MARKETING")), signupCookie(KAKAO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_TERMS_NOT_AGREED"));

        assertThat(count("SELECT count(*) FROM users")).isZero();
        assertThat(count("SELECT count(*) FROM user_social_accounts")).isZero();
    }

    @Test
    @DisplayName("아이디: 사용 중 · 예약어는 409 USERNAME_TAKEN, 형식 오류는 400 INVALID_USERNAME, 이름 21자는 400 INVALID_NAME")
    void usernameAndNameErrors() throws Exception {
        insertUser("seoyeon", null);
        List<String> required = List.of("SERVICE", "PRIVACY");

        signUp(body("SEOYEON", null, false, required), signupCookie(KAKAO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        signUp(body("pickong", null, false, required), signupCookie(KAKAO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        signUp(body("서연", null, false, required), signupCookie(KAKAO))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_USERNAME"));
        signUp(body("kong_01", "가".repeat(21), false, required), signupCookie(KAKAO))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_NAME"));
        assertThat(count("SELECT count(*) FROM users")).as("처음 넣은 회원만").isEqualTo(1);
    }

    @Test
    @DisplayName("가입 쿠키 없이: 401 SIGNUP_SESSION_EXPIRED, 로그인만 된 상태면 409 ALREADY_REGISTERED")
    void signUpWithoutSignupCookie() throws Exception {
        signUp(body("kong_01", null, false, List.of("SERVICE", "PRIVACY")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SIGNUP_SESSION_EXPIRED"));

        long userId = insertUser("seoyeon", null);
        signUp(body("kong_01", null, false, List.of("SERVICE", "PRIVACY")),
                new Cookie(AuthCookies.ACCESS_TOKEN, tokenIssuer.issueAccessToken(userId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("같은 가입 쿠키로 두 번 제출하면 두 번째는 409 SOCIAL_ACCOUNT_ALREADY_LINKED (회원이 둘 생기지 않음)")
    void doubleSubmit() throws Exception {
        Cookie cookie = signupCookie(KAKAO);
        signUp(body("kong_01", null, false, List.of("SERVICE", "PRIVACY")), cookie).andExpect(status().isOk());

        signUp(body("kong_02", null, false, List.of("SERVICE", "PRIVACY")), cookie)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_ALREADY_LINKED"));
        assertThat(count("SELECT count(*) FROM users")).isEqualTo(1);
    }

    @Test
    @DisplayName("그 사이 같은 이메일이 다른 계정에 연결됐으면 409 EMAIL_ALREADY_REGISTERED (1인 1계정)")
    void emailTakenMeanwhile() throws Exception {
        long other = insertUser("jimin", null);
        jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_id, email) VALUES (?, 'GOOGLE', '1089', 'kong@kakao.com')", other);

        signUp(body("kong_01", null, false, List.of("SERVICE", "PRIVACY")), signupCookie(KAKAO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("가입 이메일(users.email)이 이미 다른 회원에게 있으면 409 EMAIL_ALREADY_REGISTERED")
    void usersEmailTaken() throws Exception {
        insertUser("jimin", "kong@kakao.com");

        signUp(body("kong_01", null, false, List.of("SERVICE", "PRIVACY")), signupCookie(KAKAO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("가입 완료 · 그만두기도 CSRF 헤더가 없으면 403")
    void csrfRequired() throws Exception {
        mvc.perform(api(post("/api/v1/auth/signup")).cookie(signupCookie(KAKAO))
                        .contentType(MediaType.APPLICATION_JSON).content(body("kong_01", null, false, List.of("SERVICE", "PRIVACY"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        mvc.perform(api(delete("/api/v1/auth/signup")))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /auth/signup ───────────────────────────────

    @Test
    @DisplayName("가입 그만두기: 204 + 가입 쿠키 삭제")
    void cancel() throws Exception {
        MvcResult result = mvc.perform(api(delete("/api/v1/auth/signup")).with(csrf()).cookie(signupCookie(KAKAO)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(setCookie(result, AuthCookies.SIGNUP_TOKEN)).contains("Max-Age=0", "Path=/api/v1/auth");
    }
}
