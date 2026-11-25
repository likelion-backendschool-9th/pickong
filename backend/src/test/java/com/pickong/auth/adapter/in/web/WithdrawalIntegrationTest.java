package com.pickong.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.auth.application.port.out.RestoreTokenPort;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 탈퇴 신청 · 탈퇴 취소 통합 테스트. 실제 PostgreSQL · 보안 필터 · 쿠키 · CSRF를 그대로 거칩니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class WithdrawalIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    /** 진짜 동작을 그대로 쓰되, 세션 발급 실패 테스트에서만 예외를 던지게 바꿉니다 */
    @MockitoSpyBean
    TokenUseCase tokenUseCase;

    @Autowired
    RestoreTokenPort restoreTokens;

    private long me;
    private Cookie access;

    @BeforeEach
    void setUp() {
        clean();
        me = jdbc.queryForObject("INSERT INTO users (username, email) VALUES ('seoyeon', 'kong@kakao.com') RETURNING id", Long.class);
        access = new Cookie(AuthCookies.ACCESS_TOKEN, tokenUseCase.issue(me).accessToken());
        tokenUseCase.issue(me); // 다른 기기의 세션 하나 더
    }

    /** 이 테스트는 실제로 커밋하므로, 끝나면 넣은 행을 지워 같은 DB를 쓰는 다른 테스트(롤백 방식)에 남기지 않습니다. */
    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM withdrawal_reasons");
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM terms WHERE version = '9.9-withdrawal-test'");
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
    }

    private ResultActions withdraw(String json) throws Exception {
        return mvc.perform(post("/api/v1/me/withdrawal").servletPath("/api/v1").with(csrf()).cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Map<String, Object> userRow() {
        return jdbc.queryForMap("SELECT status, withdrawal_requested_at FROM users WHERE id = ?", me);
    }

    private Cookie restoreCookie() {
        return new Cookie(AuthCookies.RESTORE_TOKEN, restoreTokens.issue(me));
    }

    private static boolean clears(MvcResult result, String cookie) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(header -> header.startsWith(cookie + "=;") && header.contains("Max-Age=0"));
    }

    // ── 탈퇴 신청 ───────────────────────────────────────────

    @Test
    @DisplayName("탈퇴 신청: 204, WITHDRAWN_PENDING, 모든 기기의 세션 폐기, 쿠키 삭제, 사유는 회원 id 없이 날짜만 저장")
    void requestWithdrawal() throws Exception {
        MvcResult result = withdraw("{\"reason\":\"ETC\",\"detail\":\"  더 이상 쓰지 않아요 \"}")
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(userRow().get("status")).isEqualTo("WITHDRAWN_PENDING");
        assertThat(userRow().get("withdrawal_requested_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class, me))
                .as("두 기기의 세션 모두 폐기").isZero();
        assertThat(clears(result, AuthCookies.ACCESS_TOKEN)).isTrue();
        assertThat(clears(result, AuthCookies.REFRESH_TOKEN)).isTrue();

        List<Map<String, Object>> reasons = jdbc.queryForList("SELECT * FROM withdrawal_reasons");
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).containsEntry("reason", "ETC").containsEntry("detail", "더 이상 쓰지 않아요");
        assertThat(reasons.getFirst().keySet()).as("회원 id · 시각 컬럼이 없음").containsExactlyInAnyOrder("id", "reason", "detail", "requested_on");
    }

    @Test
    @DisplayName("탈퇴 신청: 잘못된 사유(없음 · 모름 · 기타인데 내용 없음 · 300자 초과)는 400이고 아무것도 바꾸지 않는다")
    void invalidReason() throws Exception {
        String tooLong = "가".repeat(301);
        for (String json : new String[] {"{}", "{\"reason\":\"BORED\"}", "{\"reason\":\"ETC\",\"detail\":\"  \"}",
                "{\"reason\":\"PRIVACY\",\"detail\":\"" + tooLong + "\"}"}) {
            withdraw(json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WITHDRAWAL_REASON"));
        }

        assertThat(userRow().get("status")).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawal_reasons", Integer.class)).isZero();
    }

    @Test
    @DisplayName("탈퇴 신청: 이미 탈퇴 중이면 409 WITHDRAWAL_NOT_ALLOWED (남은 Access Token으로 다시 보내도)")
    void alreadyPending() throws Exception {
        withdraw("{\"reason\":\"RARELY_USED\"}").andExpect(status().isNoContent());

        withdraw("{\"reason\":\"RARELY_USED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_NOT_ALLOWED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawal_reasons", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴 신청: 로그인하지 않으면 401, CSRF 헤더가 없으면 403")
    void requestNeedsLoginAndCsrf() throws Exception {
        mvc.perform(post("/api/v1/me/withdrawal").servletPath("/api/v1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"RARELY_USED\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/withdrawal").servletPath("/api/v1").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"RARELY_USED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        assertThat(userRow().get("status")).isEqualTo("ACTIVE");
    }

    // ── 탈퇴 취소 ───────────────────────────────────────────

    @Test
    @DisplayName("탈퇴 취소: 탈퇴 상태 조회 → 취소하면 ACTIVE, 세션 쿠키 발급, 탈퇴 취소 쿠키 삭제")
    void cancelWithdrawal() throws Exception {
        withdraw("{\"reason\":\"RARELY_USED\"}").andExpect(status().isNoContent());
        Cookie restore = restoreCookie();

        mvc.perform(get("/api/v1/auth/withdrawal").servletPath("/api/v1").cookie(restore))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("seoyeon"))
                .andExpect(jsonPath("$.requestedAt").exists())
                .andExpect(jsonPath("$.anonymizeAt").exists());

        MvcResult result = mvc.perform(post("/api/v1/auth/withdrawal/cancel").servletPath("/api/v1").with(csrf()).cookie(restore))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("seoyeon"))
                .andReturn();

        assertThat(userRow().get("status")).isEqualTo("ACTIVE");
        assertThat(userRow().get("withdrawal_requested_at")).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(header -> header.startsWith(AuthCookies.ACCESS_TOKEN + "=ey"))
                .anyMatch(header -> header.startsWith(AuthCookies.REFRESH_TOKEN + "=") && !header.contains("Max-Age=0"));
        assertThat(clears(result, AuthCookies.RESTORE_TOKEN)).isTrue();
    }

    @Test
    @DisplayName("탈퇴 취소: 쿠키가 없거나 다른 종류의 토큰이면 401 RESTORE_SESSION_EXPIRED (쿠키 삭제)")
    void restoreSessionExpired() throws Exception {
        withdraw("{\"reason\":\"RARELY_USED\"}").andExpect(status().isNoContent());

        MvcResult result = mvc.perform(get("/api/v1/auth/withdrawal").servletPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RESTORE_SESSION_EXPIRED"))
                .andReturn();
        assertThat(clears(result, AuthCookies.RESTORE_TOKEN)).isTrue();

        // Access Token을 탈퇴 취소 쿠키에 넣어도 종류가 달라 거절
        mvc.perform(post("/api/v1/auth/withdrawal/cancel").servletPath("/api/v1").with(csrf())
                        .cookie(new Cookie(AuthCookies.RESTORE_TOKEN, access.getValue())))
                .andExpect(status().isUnauthorized());
        assertThat(userRow().get("status")).isEqualTo("WITHDRAWN_PENDING");
    }

    @Test
    @DisplayName("탈퇴 취소: 30일이 지나면(익명화 전이라도) 409 WITHDRAWAL_NOT_CANCELLABLE")
    void cancelAfterGrace() throws Exception {
        withdraw("{\"reason\":\"RARELY_USED\"}").andExpect(status().isNoContent());
        jdbc.update("UPDATE users SET withdrawal_requested_at = now() - interval '30 days 1 minute' WHERE id = ?", me);

        mvc.perform(post("/api/v1/auth/withdrawal/cancel").servletPath("/api/v1").with(csrf()).cookie(restoreCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_NOT_CANCELLABLE"));
        assertThat(userRow().get("status")).isEqualTo("WITHDRAWN_PENDING");
    }

    @Test
    @DisplayName("탈퇴 취소 그만두기: 쿠키만 지우고 204, 탈퇴는 그대로")
    void leaveRestore() throws Exception {
        withdraw("{\"reason\":\"RARELY_USED\"}").andExpect(status().isNoContent());

        MvcResult result = mvc.perform(delete("/api/v1/auth/withdrawal").servletPath("/api/v1").with(csrf()).cookie(restoreCookie()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(clears(result, AuthCookies.RESTORE_TOKEN)).isTrue();
        assertThat(userRow().get("status")).isEqualTo("WITHDRAWN_PENDING");
    }

    @Test
    @DisplayName("탈퇴 중에는 남은 Refresh Token으로 재발급할 수 없다 (세션 폐기가 혹시 빠져도 막힘)")
    void pendingCannotRefresh() throws Exception {
        String refresh = tokenUseCase.issue(me).refreshToken();
        withdraw("{\"reason\":\"RARELY_USED\"}").andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1").with(csrf())
                        .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴 취소 뒤 세션 발급이 실패하면: 취소는 커밋된 채(ACTIVE) 회원 id와 함께 error 로그를 남기고 오류를 그대로 던진다 (#110 리뷰 ①)")
    void sessionIssueFailureAfterCancelIsLogged(CapturedOutput output) throws Exception {
        withdraw("{\"reason\":\"RARELY_USED\"}").andExpect(status().isNoContent());
        Cookie restore = restoreCookie();
        doThrow(new IllegalStateException("세션 발급 실패 (테스트)")).when(tokenUseCase).issue(me);

        assertThatThrownBy(() -> mvc.perform(post("/api/v1/auth/withdrawal/cancel").servletPath("/api/v1").with(csrf()).cookie(restore)))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(userRow().get("status")).as("취소는 이미 커밋됨 → 다시 로그인하면 정상 세션").isEqualTo("ACTIVE");
        assertThat(output).contains("탈퇴는 취소됐지만 세션 발급 실패 (회원 " + me + ")");
    }

    @Test
    @DisplayName("재동의가 필요한 필수 약관이 남아 있어도 탈퇴 신청은 막히지 않는다 (다른 API는 403)")
    void withdrawalAllowedWhileReconsentPending() throws Exception {
        jdbc.update("""
                INSERT INTO terms (type, version, title, content_url, is_required, effective_at, requires_reconsent)
                VALUES ('PRIVACY', '9.9-withdrawal-test', '개인정보 새 버전', '/terms/privacy', TRUE, now() - interval '1 minute', TRUE)
                """);

        mvc.perform(get("/api/v1/me/social-accounts").servletPath("/api/v1").cookie(access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_RECONSENT_REQUIRED"));
        withdraw("{\"reason\":\"PRIVACY\"}").andExpect(status().isNoContent());

        assertThat(userRow().get("status")).isEqualTo("WITHDRAWN_PENDING");
    }
}
