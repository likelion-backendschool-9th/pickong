package com.pickong.moderation.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.LoginUser;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 테스트 외부 트랜잭션 없이 HTTP 처리 종료 시 커밋과 AFTER_COMMIT까지 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminSanctionIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    private long userId;
    private long otherUserId;
    private long adminId;
    private long otherAdminId;
    private long reportId;

    @BeforeEach
    void fixtures() {
        at(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        userId = insertUser();
        otherUserId = insertUser();
        adminId = insertAdmin();
        otherAdminId = insertAdmin();
        reportId = jdbc.queryForObject("""
                INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot)
                VALUES (?,'USER',?,'SPAM','original snapshot') RETURNING id
                """, Long.class, otherUserId, userId);
        for (int index = 0; index < 2; index++) {
            jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,family,expires_at) VALUES (?,?,?,?)",
                    userId, "s8-" + UUID.randomUUID(), "s8-family-" + index, NOW.plusDays(60));
        }
    }

    @AfterEach
    void cleanOnlyOwnData() {
        jdbc.execute("ALTER TABLE admin_action_logs DROP CONSTRAINT IF EXISTS ck_s8_reject_reason");
        jdbc.update("DELETE FROM admin_action_logs WHERE admin_id IN (?,?)", adminId, otherAdminId);
        jdbc.update("DELETE FROM user_sanctions WHERE user_id IN (?,?)", userId, otherUserId);
        jdbc.update("DELETE FROM reports WHERE id=?", reportId);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (?,?)", userId, otherUserId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", userId, otherUserId);
        jdbc.update("DELETE FROM admins WHERE id IN (?,?)", adminId, otherAdminId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WARNING", "SUSPEND_7", "SUSPEND_30", "BAN"})
    void createsSanctionWithRealStateLogAndPostCommitTokenRevocation(String type) throws Exception {
        JsonNode result = create(type, "  적용 사유  ");
        long id = Long.parseLong(result.get("id").asString());
        assertThat(result.get("id").isString()).isTrue();
        assertThat(result.get("type").asString()).isEqualTo(type);
        assertThat(result.get("reason").asString()).isEqualTo("적용 사유");
        assertThat(OffsetDateTime.parse(result.get("startedAt").asString())).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT reason FROM user_sanctions WHERE id=?", String.class, id)).isEqualTo("적용 사유");
        assertThat(jdbc.queryForObject("SELECT admin_id FROM user_sanctions WHERE id=?", Long.class, id)).isEqualTo(adminId);
        assertThat(jdbc.queryForObject("SELECT source_report_id FROM user_sanctions WHERE id=?", Long.class, id)).isNull();
        if (type.startsWith("SUSPEND")) {
            OffsetDateTime until = NOW.plusHours(type.equals("SUSPEND_7") ? 168 : 720);
            assertThat(OffsetDateTime.parse(result.get("until").asString())).isEqualTo(until);
            assertThat(jdbc.queryForObject("SELECT suspended_until FROM users WHERE id=?", OffsetDateTime.class, userId)).isEqualTo(until);
            assertThat(state()).isEqualTo("SUSPENDED");
        } else {
            assertThat(result.get("until").isNull()).isTrue();
            assertThat(state()).isEqualTo(type.equals("BAN") ? "BANNED" : "ACTIVE");
            assertThat(jdbc.queryForObject("SELECT suspended_until FROM users WHERE id=?", OffsetDateTime.class, userId)).isNull();
        }
        assertThat(liveTokens()).isEqualTo(type.equals("WARNING") ? 2 : 0);
        assertThat(logCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT admin_id FROM admin_action_logs WHERE admin_id=?", Long.class, adminId)).isEqualTo(adminId);
        assertThat(jdbc.queryForObject("SELECT action_type FROM admin_action_logs WHERE admin_id=?", String.class, adminId)).isEqualTo("SANCTION_USER");
        assertReportUnchanged();
        mvc.perform(api(get("/api/v1/admin/users")).with(authentication(admin())).param("q", username()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sanction.id").value(Long.toString(id)));
    }

    @Test
    void clientCannotSupplyActorSourceUserOrPeriod() throws Exception {
        mvc.perform(createRequest(userId).content("""
                        {"type":"SUSPEND_7","reason":"직접 처리","adminId":%d,"sourceReportId":%d,
                         "userId":%d,"duration":1,"suspendedUntil":"2099-01-01T00:00:00Z"}
                        """.formatted(otherAdminId, reportId, otherUserId)))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT admin_id FROM user_sanctions WHERE user_id=?", Long.class, userId)).isEqualTo(adminId);
        assertThat(jdbc.queryForObject("SELECT source_report_id FROM user_sanctions WHERE user_id=?", Long.class, userId)).isNull();
        assertThat(jdbc.queryForObject("SELECT suspended_until FROM users WHERE id=?", OffsetDateTime.class, userId)).isEqualTo(NOW.plusHours(168));
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, otherUserId)).isEqualTo("ACTIVE");
        assertReportUnchanged();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"SUSPEND", "suspend_7", "UNKNOWN"})
    void rejectsUnknownOrMissingType(String type) throws Exception {
        expectError(createRequest(userId).content(body(type, "사유")), 400, "INVALID_SANCTION_TYPE");
        assertNoChanges();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\u00a0\u2003\u0085"})
    void rejectsBlankReason(String reason) throws Exception {
        expectError(createRequest(userId).content(body("WARNING", reason)), 400, "SANCTION_REASON_REQUIRED");
        assertNoChanges();
    }

    @Test
    void reasonLimitCountsUnicodeCodePoints() throws Exception {
        expectError(createRequest(userId).content(body("WARNING", "😀".repeat(201))), 400, "SANCTION_REASON_TOO_LONG");
        assertNoChanges();
        JsonNode result = create("WARNING", "😀".repeat(200));
        assertThat(result.get("reason").asString().codePointCount(0, result.get("reason").asString().length())).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "{\"type\":{},\"reason\":\"사유\"}", "{\"type\":\"WARNING\",\"reason\":{}}"})
    void invalidJsonUsesCommonErrorContract(String body) throws Exception {
        expectError(createRequest(userId).content(body), 400, "INVALID_INPUT");
        assertNoChanges();
    }

    @Test
    void emptyCreateBodyIsRejected() throws Exception {
        expectError(createRequest(userId), 400, "INVALID_SANCTION_TYPE");
        assertNoChanges();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void validatesBothPathIdentifiers(String badId) throws Exception {
        expectError(adminRequest(post("/api/v1/admin/users/" + badId + "/sanctions")).content(body("BAN", "사유")),
                400, "INVALID_INPUT");
        expectError(adminRequest(delete("/api/v1/admin/users/" + userId + "/sanctions/" + badId)), 400, "INVALID_INPUT");
        expectError(adminRequest(delete("/api/v1/admin/users/" + badId + "/sanctions/1")), 400, "INVALID_INPUT");
        assertNoChanges();
    }

    @Test
    void missingUserReturns404ForCreateAndRevoke() throws Exception {
        expectError(createRequest(Long.MAX_VALUE).content(body("BAN", "사유")), 404, "USER_NOT_FOUND");
        expectError(adminRequest(delete("/api/v1/admin/users/" + Long.MAX_VALUE + "/sanctions/1")), 404, "USER_NOT_FOUND");
        assertNoChanges();
    }

    @ParameterizedTest
    @ValueSource(strings = {"WITHDRAWN_PENDING", "WITHDRAWN"})
    void cannotSanctionWithdrawnAccounts(String status) throws Exception {
        jdbc.update("UPDATE users SET status=? WHERE id=?", status, userId);
        expectError(createRequest(userId).content(body("WARNING", "사유")), 409, "USER_SANCTION_NOT_ALLOWED");
        assertThat(state()).isEqualTo(status);
        assertThat(sanctionCount()).isZero();
        assertThat(logCount()).isZero();
        assertThat(liveTokens()).isEqualTo(2);
    }

    @Test
    void repeatedWarningsCanBeRevokedIndividuallyWithoutLiftingBan() throws Exception {
        String first = create("WARNING", "첫 경고").get("id").asString();
        String second = create("WARNING", "두번째 경고").get("id").asString();
        String ban = create("BAN", "영구 정지").get("id").asString();
        mvc.perform(revokeRequest(userId, first)).andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
        assertThat(state()).isEqualTo("BANNED");
        assertThat(open(second)).isTrue();
        assertThat(open(ban)).isTrue();
        assertThat(jdbc.queryForObject("SELECT reason FROM user_sanctions WHERE id=?", String.class, Long.valueOf(first))).isEqualTo("첫 경고");
        assertThat(jdbc.queryForObject("SELECT revoke_reason FROM user_sanctions WHERE id=?", String.class, Long.valueOf(first))).isNull();
        assertThat(sanctionCount()).isEqualTo(3);
        assertThat(logCount()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPEND_7", "BAN"})
    void revokeRestrictionRestoresAccountButPreservesWarningAndHistory(String type) throws Exception {
        String warning = create("WARNING", "남은 경고").get("id").asString();
        JsonNode created = create(type, "원래 사유");
        String id = created.get("id").asString();
        mvc.perform(revokeRequest(userId, id).content("{\"reason\":\"  검토 후 해제  \"}"))
                .andExpect(status().isNoContent());
        assertThat(state()).isEqualTo("ACTIVE");
        assertThat(open(warning)).isTrue();
        assertThat(jdbc.queryForObject("SELECT reason FROM user_sanctions WHERE id=?", String.class, Long.valueOf(id))).isEqualTo("원래 사유");
        assertThat(jdbc.queryForObject("SELECT revoke_reason FROM user_sanctions WHERE id=?", String.class, Long.valueOf(id))).isEqualTo("검토 후 해제");
        assertThat(jdbc.queryForObject("SELECT revoked_by_admin_id FROM user_sanctions WHERE id=?", Long.class, Long.valueOf(id))).isEqualTo(adminId);
        assertThat(jdbc.queryForObject("SELECT end_reason FROM user_sanctions WHERE id=?", String.class, Long.valueOf(id))).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM user_sanctions WHERE id=?", OffsetDateTime.class, Long.valueOf(id))).isEqualTo(NOW);
        assertThat(liveTokens()).isZero();
        assertThat(sanctionCount()).isEqualTo(2);
        assertThat(logCount()).isEqualTo(3);
        assertReportUnchanged();
    }

    @Test
    void ownershipAndAlreadyRevokedChecksDoNotAddLogs() throws Exception {
        String id = create("WARNING", "사유").get("id").asString();
        expectError(revokeRequest(otherUserId, id), 404, "SANCTION_NOT_FOUND");
        expectError(revokeRequest(userId, Long.toString(Long.MAX_VALUE)), 404, "SANCTION_NOT_FOUND");
        assertThat(logCount()).isEqualTo(1);
        assertThat(open(id)).isTrue();
        mvc.perform(revokeRequest(userId, id)).andExpect(status().isNoContent());
        expectError(revokeRequest(userId, id), 409, "SANCTION_ALREADY_ENDED");
        assertThat(logCount()).isEqualTo(2);
        assertThat(sanctionCount()).isEqualTo(1);
    }

    @Test
    void revokeReasonLimitAndInvalidBodyDoNotPartiallyEndSanction() throws Exception {
        String id = create("WARNING", "원래 사유").get("id").asString();
        expectError(revokeRequest(userId, id).content("{\"reason\":\"" + "😀".repeat(201) + "\"}"),
                400, "SANCTION_REASON_TOO_LONG");
        expectError(revokeRequest(userId, id).content("{"), 400, "INVALID_INPUT");
        assertThat(open(id)).isTrue();
        assertThat(logCount()).isEqualTo(1);
        mvc.perform(revokeRequest(userId, id).content("{\"reason\":\"" + "😀".repeat(200) + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(open(id)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"  \"}"})
    void optionalEmptyRevokeReasonIsNull(String body) throws Exception {
        String id = create("WARNING", "사유").get("id").asString();
        mvc.perform(revokeRequest(userId, id).content(body)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT revoke_reason FROM user_sanctions WHERE id=?", String.class, Long.valueOf(id))).isNull();
    }

    @Test
    void revokeUsesAuthenticatedAdminAndDoesNotRestoreWithdrawal() throws Exception {
        String id = create("SUSPEND_7", "원래 사유").get("id").asString();
        jdbc.update("UPDATE users SET status='WITHDRAWN_PENDING',suspended_until=NULL WHERE id=?", userId);
        mvc.perform(revokeRequest(userId, id).content("{\"adminId\":" + otherAdminId + ",\"reason\":\"해제\"}"))
                .andExpect(status().isNoContent());
        assertThat(state()).isEqualTo("WITHDRAWN_PENDING");
        assertThat(jdbc.queryForObject("SELECT revoked_by_admin_id FROM user_sanctions WHERE id=?", Long.class, Long.valueOf(id))).isEqualTo(adminId);
    }

    @Test
    void activeRestrictionConflictAndBanUpgradeUseExistingRules() throws Exception {
        String old = create("SUSPEND_7", "첫 정지").get("id").asString();
        expectError(createRequest(userId).content(body("SUSPEND_30", "연장 시도")), 409, "ACTIVE_SANCTION_EXISTS");
        create("BAN", "영구 정지 전환");
        assertThat(jdbc.queryForObject("SELECT end_reason FROM user_sanctions WHERE id=?", String.class, Long.valueOf(old))).isEqualTo("SUPERSEDED_BY_BAN");
        assertThat(jdbc.queryForObject("SELECT reason FROM user_sanctions WHERE id=?", String.class, Long.valueOf(old))).isEqualTo("첫 정지");
        expectError(createRequest(userId).content(body("BAN", "중복")), 409, "ACTIVE_SANCTION_EXISTS");
        expectError(createRequest(userId).content(body("SUSPEND_7", "중복")), 409, "ACTIVE_SANCTION_EXISTS");
        assertThat(sanctionCount()).isEqualTo(2);
        assertThat(logCount()).isEqualTo(2);
        assertThat(state()).isEqualTo("BANNED");
    }

    @Test
    void expiredTargetCannotBeManuallyRevokedAndNewSanctionClosesOldHistory() throws Exception {
        String old = create("SUSPEND_7", "첫 정지").get("id").asString();
        at(NOW.plusHours(168));
        expectError(revokeRequest(userId, old), 409, "SANCTION_ALREADY_EXPIRED");
        assertThat(open(old)).isTrue();
        assertThat(logCount()).isEqualTo(1);
        create("SUSPEND_30", "새 정지");
        assertThat(jdbc.queryForObject("SELECT end_reason FROM user_sanctions WHERE id=?", String.class, Long.valueOf(old))).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT ended_at FROM user_sanctions WHERE id=?", OffsetDateTime.class, Long.valueOf(old))).isEqualTo(NOW.plusHours(168));
        assertThat(logCount()).isEqualTo(2);
    }

    @Test
    void failedLogRollsBackAccountSanctionAndKeepsTokens() throws Exception {
        jdbc.execute("ALTER TABLE admin_action_logs ADD CONSTRAINT ck_s8_reject_reason CHECK (reason IS DISTINCT FROM 's8-rollback')");
        expectError(createRequest(userId).content(body("BAN", "s8-rollback")), 500, "INTERNAL_SERVER_ERROR");
        assertNoChanges();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mutationsRequireCsrfEvenForAdmins(boolean deleting) throws Exception {
        MockHttpServletRequestBuilder request = mutation(deleting).servletPath("/api/v1")
                .with(authentication(admin())).contentType(MediaType.APPLICATION_JSON);
        if (!deleting) request.content(body("BAN", "사유"));
        expectError(request, 403, "INVALID_CSRF_TOKEN");
        assertNoChanges();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anonymousWithCsrfIs401(boolean deleting) throws Exception {
        expectError(api(mutation(deleting)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("BAN", "사유")),
                401, "UNAUTHORIZED");
        assertNoChanges();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryMemberWithCsrfIs403(boolean deleting) throws Exception {
        expectError(api(mutation(deleting)).with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .contentType(MediaType.APPLICATION_JSON).content(body("BAN", "사유")), 403, "FORBIDDEN");
        assertNoChanges();
    }

    @Test
    void adminRoleWithoutAdminPrincipalCannotWrite() throws Exception {
        expectError(api(post("/api/v1/admin/users/" + userId + "/sanctions")).with(csrf())
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated("fake-admin", null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                        .contentType(MediaType.APPLICATION_JSON).content(body("BAN", "사유")), 401, "UNAUTHORIZED");
        assertNoChanges();
    }

    private MockHttpServletRequestBuilder mutation(boolean deleting) {
        return deleting ? delete("/api/v1/admin/users/" + userId + "/sanctions/1")
                : post("/api/v1/admin/users/" + userId + "/sanctions");
    }

    private MockHttpServletRequestBuilder createRequest(long target) {
        return adminRequest(post("/api/v1/admin/users/" + target + "/sanctions"));
    }

    private MockHttpServletRequestBuilder revokeRequest(long target, String sanctionId) {
        return adminRequest(delete("/api/v1/admin/users/" + target + "/sanctions/" + sanctionId));
    }

    private MockHttpServletRequestBuilder adminRequest(MockHttpServletRequestBuilder builder) {
        return api(builder).with(csrf()).with(authentication(admin())).contentType(MediaType.APPLICATION_JSON);
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private UsernamePasswordAuthenticationToken admin() {
        return UsernamePasswordAuthenticationToken.authenticated(new AdminPrincipal(adminId), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private JsonNode create(String type, String reason) throws Exception {
        return JSON.readTree(mvc.perform(createRequest(userId).content(body(type, reason)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static String body(String type, String reason) {
        var node = JSON.createObjectNode();
        if (type != null) node.put("type", type);
        if (reason != null) node.put("reason", reason);
        return JSON.writeValueAsString(node);
    }

    private void expectError(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    private void assertNoChanges() {
        assertThat(state()).isEqualTo("ACTIVE");
        assertThat(sanctionCount()).isZero();
        assertThat(logCount()).isZero();
        assertThat(liveTokens()).isEqualTo(2);
        assertReportUnchanged();
    }

    private void assertReportUnchanged() {
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id=?", String.class, reportId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE id=?", String.class, reportId)).isEqualTo("original snapshot");
    }

    private String state() { return jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, userId); }
    private String username() { return jdbc.queryForObject("SELECT username FROM users WHERE id=?", String.class, userId); }
    private long sanctionCount() { return jdbc.queryForObject("SELECT count(*) FROM user_sanctions WHERE user_id=?", Long.class, userId); }
    private long logCount() { return jdbc.queryForObject("SELECT count(*) FROM admin_action_logs WHERE admin_id=?", Long.class, adminId); }
    private long liveTokens() { return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id=? AND revoked_at IS NULL", Long.class, userId); }
    private boolean open(String id) { return jdbc.queryForObject("SELECT ended_at IS NULL FROM user_sanctions WHERE id=?", Boolean.class, Long.valueOf(id)); }
    private void at(OffsetDateTime now) { given(clock.instant()).willReturn(now.toInstant()); }

    private long insertUser() {
        return jdbc.queryForObject("INSERT INTO users(username) VALUES (?) RETURNING id", Long.class,
                "s8" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    private long insertAdmin() {
        return jdbc.queryForObject("INSERT INTO admins(username,password_hash,role,status) VALUES (?,'test-only','OPERATOR','ACTIVE') RETURNING id",
                Long.class, "s8-" + UUID.randomUUID());
    }
}
