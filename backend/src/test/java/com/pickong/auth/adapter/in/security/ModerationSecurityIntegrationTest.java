package com.pickong.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.application.dto.IssuedTokens;
import com.pickong.auth.application.dto.SocialLoginResult;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.in.LoginUseCase;
import com.pickong.auth.application.port.in.SocialAccountUseCase;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.auth.application.port.out.TokenIssuerPort;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.common.error.BusinessException;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.LoginUser;
import com.pickong.moderation.application.dto.SanctionCommand;
import com.pickong.moderation.application.port.in.SanctionUseCase;
import com.pickong.moderation.application.port.out.SanctionPersistencePort;
import com.pickong.moderation.domain.model.Sanction;
import com.pickong.moderation.domain.vo.SanctionEndReason;
import com.pickong.moderation.domain.vo.SanctionType;
import com.pickong.user.application.port.in.UserAccountUseCase;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 실제 서명 쿠키·보안 체인·제재 UseCase·PostgreSQL을 연결합니다. 관리자 주체 주입은 테스트에서만 합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ModerationSecurityIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    @MockitoBean Clock clock;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenUseCase tokens;
    @Autowired TokenIssuerPort signer;
    @Autowired LoginUseCase login;
    @Autowired SocialAccountUseCase socialAccounts;
    @Autowired UserAccountUseCase accounts;
    @Autowired SanctionUseCase sanctions;
    @Autowired SanctionPersistencePort histories;
    private long userId;
    private long adminId;
    private long publicFeed;
    private long hiddenFeed;
    private UUID feedPublicId;
    private SocialProfile social;
    private IssuedTokens session;
    private Cookie access;

    @BeforeEach
    void fixtures() {
        at(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        String key = UUID.randomUUID().toString();
        userId = jdbc.queryForObject("INSERT INTO users(username) VALUES (?) RETURNING id", Long.class,
                "s12" + key.replace("-", "").substring(0, 12));
        adminId = jdbc.queryForObject("INSERT INTO admins(username,password_hash,role,status) VALUES (?,'test-only','OPERATOR','ACTIVE') RETURNING id",
                Long.class, "s12-" + key);
        jdbc.update("INSERT INTO user_terms_agreements(user_id,terms_id,agreed_at) SELECT ?,id,? FROM terms WHERE is_required=true", userId, NOW);
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_id) VALUES (?,'KAKAO',?)", userId, key);
        social = new SocialProfile(SocialProvider.KAKAO, key, null, null, null);
        publicFeed = jdbc.queryForObject("INSERT INTO feeds(user_id,content,visibility) VALUES (?,'공개 게시물','PUBLIC') RETURNING id", Long.class, userId);
        hiddenFeed = jdbc.queryForObject("INSERT INTO feeds(user_id,content,visibility,hidden_at) VALUES (?,'숨김 게시물','PUBLIC',?) RETURNING id", Long.class, userId, NOW);
        feedPublicId = jdbc.queryForObject("SELECT public_id FROM feeds WHERE id=?", UUID.class, publicFeed);
        session = tokens.issue(userId);
        tokens.issue(userId); // 다른 기기도 함께 폐기되는지 확인합니다.
        access = new Cookie(AuthCookies.ACCESS_TOKEN, session.accessToken());
    }

    @AfterEach
    void cleanOwnData() {
        jdbc.update("DELETE FROM admin_action_logs WHERE admin_id=?", adminId);
        jdbc.update("DELETE FROM user_sanctions WHERE user_id=?", userId);
        jdbc.update("DELETE FROM reports WHERE reporter_id=?", userId);
        jdbc.update("DELETE FROM feeds WHERE user_id=?", userId);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_social_accounts WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_terms_agreements WHERE user_id=?", userId);
        jdbc.update("DELETE FROM users WHERE id=?", userId);
        jdbc.update("DELETE FROM admins WHERE id=?", adminId);
    }

    @Test
    void warningAllowsLoginRefreshAndMemberAccessWithoutGrantingAdmin() throws Exception {
        sanction(SanctionType.WARNING);
        mvc.perform(api(get("/api/v1/me")).cookie(access)).andExpect(status().isOk());
        assertThat(login.loginWithSocial(social)).isEqualTo(new SocialLoginResult.ExistingMember(userId));
        assertThat(tokens.refresh(session.refreshToken()).accessToken()).isNotBlank();
        mvc.perform(api(get("/api/v1/admin/users")).cookie(access)).andExpect(status().isForbidden());
        assertThat(liveTokens()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPEND", "BAN"})
    void restrictionImmediatelyBlocksOldAccessLoginAndAllRefreshSessions(String type) throws Exception {
        sanction(SanctionType.valueOf(type));
        mvc.perform(api(get("/api/v1/me")).cookie(access)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(api(post("/api/v1/feeds")).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        // 비회원 공개 조회는 허용하지만, 숨김 콘텐츠 작성자 권한은 더 이상 얻지 못합니다.
        mvc.perform(api(get("/api/v1/feeds/" + publicFeed)).cookie(access)).andExpect(status().isOk());
        mvc.perform(api(get("/api/v1/feeds/" + hiddenFeed)).cookie(access)).andExpect(status().isNotFound());
        assertThat(login.loginWithSocial(social)).isInstanceOf(SocialLoginResult.Unavailable.class);
        assertCode(() -> tokens.issue(userId), "ACCOUNT_UNAVAILABLE");
        assertCode(() -> tokens.refresh(session.refreshToken()), "REFRESH_TOKEN_REUSED");
        assertThat(liveTokens()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "BANNED", "WITHDRAWN_PENDING", "WITHDRAWN"})
    void statusCheckRevokesSurvivingSessionsEvenIfEarlierRevocationFailed(String state) throws Exception {
        // 이벤트 처리 실패 등으로 남은 세션도 재발급 시 계정 상태를 직접 확인해야 합니다.
        jdbc.update("UPDATE users SET status=?,suspended_until=NULL WHERE id=?", state, userId);
        mvc.perform(api(get("/api/v1/me")).cookie(access)).andExpect(status().isUnauthorized());
        assertCode(() -> tokens.refresh(session.refreshToken()), "ACCOUNT_UNAVAILABLE");
        assertThat(liveTokens()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, userId)).isEqualTo(state);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP", "LOGIN", "ISSUE", "REFRESH"})
    void eachAuthenticationEntryPointExpiresDueSanctionAtExactBoundary(String entry) throws Exception {
        Sanction warning = sanction(SanctionType.WARNING);
        Sanction suspension = sanction(SanctionType.SUSPEND);
        at(suspension.getSuspendedUntil().minusSeconds(1));
        // 서명 검증기는 계정 정책과 독립적입니다. 만료 경계에 유효한 쿠키를 테스트용으로 준비합니다.
        Cookie boundaryCookie = new Cookie(AuthCookies.ACCESS_TOKEN, signer.issueAccessToken(userId));
        mvc.perform(api(get("/api/v1/me")).cookie(boundaryCookie)).andExpect(status().isUnauthorized());
        assertThat(histories.findById(suspension.getId()).orElseThrow().getEndedAt()).isNull();
        if (entry.equals("REFRESH")) {
            // AFTER_COMMIT 폐기 실패로 남아 있는 토큰도 만료 판단을 스케줄러에만 의존하지 않습니다.
            jdbc.update("UPDATE refresh_tokens SET revoked_at=NULL WHERE user_id=?", userId);
        }
        at(suspension.getSuspendedUntil());
        switch (entry) {
            case "HTTP" -> mvc.perform(api(get("/api/v1/me")).cookie(boundaryCookie)).andExpect(status().isOk());
            case "LOGIN" -> assertThat(login.loginWithSocial(social)).isEqualTo(new SocialLoginResult.ExistingMember(userId));
            case "ISSUE" -> assertThat(tokens.issue(userId).refreshToken()).isNotBlank();
            case "REFRESH" -> assertThat(tokens.refresh(session.refreshToken()).refreshToken()).isNotBlank();
            default -> throw new AssertionError(entry);
        }
        assertThat(accounts.canLogin(userId)).isTrue();
        Sanction expired = histories.findById(suspension.getId()).orElseThrow();
        assertThat(expired.getEndReason()).isEqualTo(SanctionEndReason.EXPIRED);
        assertThat(expired.getEndedAt()).isEqualTo(suspension.getSuspendedUntil());
        assertThat(expired.getRevokedAt()).isNull();
        assertThat(histories.findOpenByUserId(userId)).extracting(Sanction::getId).containsExactly(warning.getId());
        assertThat(logCount()).isEqualTo(2); // 자동 만료에는 관리자 작업을 꾸며 기록하지 않습니다.
        if (!entry.equals("REFRESH")) assertCode(() -> tokens.refresh(session.refreshToken()), "REFRESH_TOKEN_REUSED");
    }

    @Test
    void expiredAccountDeadlineDoesNotLiftAnotherEffectiveRestriction() throws Exception {
        Sanction ban = sanction(SanctionType.BAN);
        // 계정의 오래된 기간 정지 값이 남아 있어도 moderation의 유효한 BAN을 무시하면 안 됩니다.
        jdbc.update("UPDATE users SET status='SUSPENDED',suspended_until=? WHERE id=?", NOW.minusSeconds(1), userId);
        mvc.perform(api(get("/api/v1/me")).cookie(access)).andExpect(status().isUnauthorized());
        assertThat(accounts.canLogin(userId)).isFalse();
        assertCode(() -> tokens.issue(userId), "ACCOUNT_UNAVAILABLE");
        assertThat(histories.findById(ban.getId()).orElseThrow().getEndedAt()).isNull();
        assertThat(logCount()).isEqualTo(1);
        assertThat(liveTokens()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BANNED", "WITHDRAWN_PENDING", "WITHDRAWN"})
    void legacyDeadlineNeverRestoresBannedOrWithdrawnAccounts(String state) throws Exception {
        jdbc.update("UPDATE users SET status=?,suspended_until=? WHERE id=?", state, NOW.minusDays(1), userId);
        mvc.perform(api(get("/api/v1/me")).cookie(access)).andExpect(status().isUnauthorized());
        assertCode(() -> tokens.issue(userId), "ACCOUNT_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, userId)).isEqualTo(state);
        assertThat(histories.findByUserId(userId)).isEmpty();
        assertThat(logCount()).isZero();
    }

    @Test
    void validSignatureForMissingAccountHasNoMemberOrAdminPrivilege() throws Exception {
        Cookie missing = new Cookie(AuthCookies.ACCESS_TOKEN, signer.issueAccessToken(Long.MAX_VALUE));
        mvc.perform(api(get("/api/v1/me")).cookie(missing)).andExpect(status().isUnauthorized());
        mvc.perform(api(get("/api/v1/admin/users")).cookie(missing)).andExpect(status().isUnauthorized());
        mvc.perform(api(get("/api/v1/feeds/" + publicFeed)).cookie(missing)).andExpect(status().isOk());
        assertThat(logCount()).isZero();
    }

    @Test
    void oldAccessCannotStartOAuthLinkAfterBan() throws Exception {
        String link = socialAccounts.startLink(userId, SocialProvider.GOOGLE);
        sanction(SanctionType.BAN);
        var response = mvc.perform(api(get("/api/v1/auth/oauth2/authorization/google")).param("link", link).cookie(access))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        assertThat(response.getRedirectedUrl()).doesNotContain("accounts.google.com").contains("error=");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_social_accounts WHERE user_id=?", Long.class, userId)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPER_ADMIN", "OPERATOR", "CS"})
    void activeAdminRolesCanReadAllExistingAdminLists(String role) throws Exception {
        jdbc.update("UPDATE admins SET role=? WHERE id=?", role, adminId);
        for (String path : List.of("users", "feeds", "reports", "place-submissions")) {
            mvc.perform(asAdmin(get("/api/v1/admin/" + path)).param("status", "PENDING")).andExpect(status().isOk());
        }
        mvc.perform(asAdmin(get("/api/v1/feeds/" + hiddenFeed))).andExpect(status().isOk());
        assertThat(logCount()).isZero();
    }

    @Test
    void disabledOrMissingAdminCannotReadMutateOrUseHiddenContentPrivilege() throws Exception {
        jdbc.update("UPDATE admins SET status='DISABLED' WHERE id=?", adminId);
        for (String path : List.of("users", "feeds", "reports", "place-submissions")) {
            mvc.perform(asAdmin(get("/api/v1/admin/" + path))).andExpect(status().isUnauthorized());
        }
        mvc.perform(asAdmin(post("/api/v1/admin/users/" + userId + "/sanctions")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"BAN\",\"reason\":\"차단\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(asAdmin(get("/api/v1/feeds/" + hiddenFeed))).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM admins WHERE id=?", adminId);
        mvc.perform(asAdmin(get("/api/v1/admin/users"))).andExpect(status().isUnauthorized());
        assertThat(logCount()).isZero();
        assertThat(histories.findByUserId(userId)).isEmpty();
        assertThat(accounts.canLogin(userId)).isTrue();
    }

    @Test
    void memberIdentityWithAdminRoleAndAdminIdentityWithoutRoleAreRejected() throws Exception {
        var fakeMember = UsernamePasswordAuthenticationToken.authenticated(new LoginUser(userId), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var missingRole = UsernamePasswordAuthenticationToken.authenticated(new AdminPrincipal(adminId), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        for (var invalid : List.of(fakeMember, missingRole)) {
            mvc.perform(api(get("/api/v1/admin/users")).with(authentication(invalid))).andExpect(status().isUnauthorized());
        }
    }

    @Test
    // csrf() 테스트 후처리기가 바꾼 저장소가 남지 않도록, 실제 쿠키 흐름은 새 컨텍스트에서 확인합니다.
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void realCsrfCookieAndPrincipalDetermineAuditActorAndNoOpDoesNotLog() throws Exception {
        var first = mvc.perform(asAdmin(get("/api/v1/admin/users"))).andExpect(status().isOk()).andReturn();
        Cookie xsrf = first.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        String path = "/api/v1/admin/feeds/" + feedPublicId;
        mvc.perform(asAdmin(patch(path)).contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true,\"reason\":\"확인\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        assertThat(logCount()).isZero();
        for (int i = 0; i < 2; i++) {
            mvc.perform(asAdmin(patch(path)).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true,\"reason\":\"확인\",\"adminId\":999999}"))
                    .andExpect(status().isOk());
        }
        assertThat(logCount()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT admin_id,action_type,target_type,target_id,reason FROM admin_action_logs WHERE admin_id=?", adminId))
                .containsEntry("admin_id", adminId).containsEntry("action_type", "HIDE_CONTENT")
                .containsEntry("target_type", "FEED").containsEntry("target_id", publicFeed).containsEntry("reason", "확인");
        mvc.perform(asAdmin(patch(path)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":false}"))
                .andExpect(status().isOk());
        assertThat(logCount()).isEqualTo(2);
    }

    @Test
    void adminRevokePreservesHistoryAndDoesNotReviveRefreshSessions() throws Exception {
        Sanction ban = sanction(SanctionType.BAN);
        mvc.perform(asAdmin(delete("/api/v1/admin/users/" + userId + "/sanctions/" + ban.getId())).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(accounts.canLogin(userId)).isTrue();
        assertThat(histories.findById(ban.getId()).orElseThrow().getRevokedByAdminId()).isEqualTo(adminId);
        assertCode(() -> tokens.refresh(session.refreshToken()), "REFRESH_TOKEN_REUSED");
        assertThat(liveTokens()).isZero();
        assertThat(logCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ISSUE", "REFRESH"})
    void tokenWritesAndBanHaveConsistentLockOrderAndLeaveNoLiveSession(String operation) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Supplier<String>> work = List.of(
                    () -> outcome(() -> { sanction(SanctionType.BAN); return null; }),
                    () -> outcome(() -> operation.equals("ISSUE") ? tokens.issue(userId) : tokens.refresh(session.refreshToken())));
            var futures = work.stream().map(task -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 시작 실패");
                return task.get();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(futures.get(0).get(20, TimeUnit.SECONDS)).isEqualTo("OK");
            assertThat(futures.get(1).get(20, TimeUnit.SECONDS)).isIn("OK", "ACCOUNT_UNAVAILABLE", "REFRESH_TOKEN_REUSED");
            assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, userId)).isEqualTo("BANNED");
            assertThat(liveTokens()).isZero();
            assertThat(logCount()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Sanction sanction(SanctionType type) {
        return sanctions.sanction(new SanctionCommand(userId, type, "정책 확인", type == SanctionType.SUSPEND ? Sanction.SEVEN_DAYS : null, adminId, null));
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder request) { return request.servletPath("/api/v1"); }
    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return api(request).with(authentication(UsernamePasswordAuthenticationToken.authenticated(new AdminPrincipal(adminId), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))));
    }
    private void at(OffsetDateTime time) { given(clock.instant()).willReturn(time.toInstant()); }
    private long liveTokens() { return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id=? AND revoked_at IS NULL", Long.class, userId); }
    private long logCount() { return jdbc.queryForObject("SELECT count(*) FROM admin_action_logs WHERE admin_id=?", Long.class, adminId); }
    private static String outcome(Supplier<?> work) {
        try { work.get(); return "OK"; }
        catch (BusinessException error) { return ((Enum<?>) error.getErrorCode()).name(); }
    }
    private static void assertCode(ThrowingCallable work, String expected) {
        assertThatThrownBy(work).isInstanceOf(BusinessException.class)
                .extracting(error -> ((Enum<?>) ((BusinessException) error).getErrorCode()).name()).isEqualTo(expected);
    }
}
