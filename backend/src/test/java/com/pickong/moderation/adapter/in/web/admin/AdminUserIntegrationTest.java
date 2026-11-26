package com.pickong.moderation.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 보안 필터 + Flyway를 적용한 격리 DB. 테스트 데이터는 각 테스트 트랜잭션에서 롤백합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminUserIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    private String prefix;
    private long adminId;

    @BeforeEach
    void setUp() {
        prefix = "s7" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        given(clock.instant()).willReturn(NOW.toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        adminId = jdbc.queryForObject("""
                INSERT INTO admins(username,password_hash,role,status)
                VALUES (?,'test-only','OPERATOR','ACTIVE') RETURNING id
                """, Long.class, prefix);
    }

    @Test
    void defaultContractKeepsIdsAsStringsAndUnknownCreatorAsNull() throws Exception {
        long id = 9_007_199_254_740_993L;
        jdbc.update("INSERT INTO users(id,username,display_name,email,password_hash) VALUES (?,?,?,?,?)",
                id, prefix, "목록 이름", "private@example.test", "private-password-hash");
        long sanctionId = sanction(id, "WARNING", "경고 사유", NOW, null);
        mvc.perform(request().param("q", prefix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(Long.toString(id)))
                .andExpect(jsonPath("$[0].nickname").value("목록 이름"))
                .andExpect(jsonPath("$[0].isCreator").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].reportCount").value(0))
                .andExpect(jsonPath("$[0].postCount").value(0))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].sanction.id").value(Long.toString(sanctionId)))
                .andExpect(jsonPath("$[0].sanctions[0].type").value("WARNING"))
                .andExpect(jsonPath("$[0].sanctions[0].startedAt").exists())
                .andExpect(jsonPath("$[0].sanctions[0].until").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void countsLivePostsIncludingHiddenButNotDeletedAndIgnoresStaleCounter() throws Exception {
        long subject = user("subject", null, "ACTIVE");
        long other = user("other", null, "ACTIVE");
        feed(subject, null, null);
        feed(subject, NOW.minusHours(1), null);
        feed(subject, null, NOW.minusHours(1));
        feed(other, null, null);
        jdbc.update("UPDATE users SET feed_count=99 WHERE id=?", subject);
        assertThat(row(list(prefix, "ALL"), subject).get("postCount").asLong()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT feed_count FROM users WHERE id=?", Long.class, subject)).isEqualTo(99);
    }

    @Test
    void countsReportHistoryOfTheActualTargetAuthorRegardlessOfVisibilityOrResolution() throws Exception {
        long author = user("author", null, "ACTIVE");
        long commenter = user("commenter", null, "ACTIVE");
        long reporter = user("reporter", null, "ACTIVE");
        long feed = feed(author, NOW, NOW);
        long comment = jdbc.queryForObject("INSERT INTO comments(feed_id,user_id,content,hidden_at,deleted_at) VALUES (?,?,'내용',?,?) RETURNING id",
                Long.class, feed, commenter, NOW, NOW);
        report(reporter, "USER", author, null, false);
        report(reporter, "FEED", feed, "{\"reportedUserId\":" + commenter + "}", true);
        report(reporter, "COMMENT", comment, null, false);
        List<JsonNode> result = list(prefix, "REPORTED");
        assertThat(result).extracting(node -> node.get("id").asString())
                .containsExactly(Long.toString(author), Long.toString(commenter));
        assertThat(row(result, author).get("reportCount").asLong()).isEqualTo(2);
        assertThat(row(result, commenter).get("reportCount").asLong()).isEqualTo(1);
        assertThat(row(list(prefix, null), reporter).get("reportCount").asLong()).isZero();
    }

    @Test
    void missingTargetsUseValidSnapshotAuthorAndNeverGuessFromTargetId() throws Exception {
        long author = user("author", null, "ACTIVE");
        long reporter = user("reporter", null, "ACTIVE");
        long missing = Long.MAX_VALUE;
        report(reporter, "FEED", missing, "{\"reportedUserId\":\"" + author + "\",\"text\":\"saved\"}", false);
        report(reporter, "COMMENT", missing, "{\"reportedUserId\":" + author + "}", true);
        report(reporter, "COLLECTION", missing, "{\"reportedUserId\":" + author + "}", false);
        report(reporter, "FEED", author, "legacy plain text", false);
        report(reporter, "COMMENT", author, "{broken", false);
        assertThat(row(list(prefix, "REPORTED"), author).get("reportCount").asLong()).isEqualTo(3);
    }

    @Test
    void reportedIncludesResolvedOnlyHistory() throws Exception {
        long author = user("author", null, "ACTIVE");
        long reporter = user("reporter", null, "ACTIVE");
        report(reporter, "USER", author, null, true);
        assertThat(list(prefix, "REPORTED")).extracting(node -> node.get("id").asString())
                .containsExactly(Long.toString(author));
    }

    @Test
    void sanctionedIncludesWarningsButExcludesExpiredRevokedAndFutureRows() throws Exception {
        long warning = user("warning", null, "ACTIVE");
        long expired = user("expired", null, "SUSPENDED");
        long revoked = user("revoked", null, "ACTIVE");
        long future = user("future", null, "ACTIVE");
        sanction(warning, "WARNING", "유효 경고", NOW.minusDays(1), null);
        sanction(expired, "SUSPEND", "만료", NOW.minusDays(7), NOW);
        long ended = sanction(revoked, "WARNING", "해제", NOW.minusDays(1), null);
        revoke(ended);
        sanction(future, "WARNING", "시작 전", NOW.plusHours(1), null);
        assertThat(list(prefix, "SANCTIONED")).extracting(node -> node.get("id").asString())
                .containsExactly(Long.toString(warning));
    }

    @Test
    void representativePrefersRestrictionWhileAllWarningsKeepTheirIds() throws Exception {
        long subject = user("subject", null, "BANNED");
        long oldWarning = sanction(subject, "WARNING", "경고1", NOW.minusHours(3), null);
        long ban = sanction(subject, "BAN", "영구 정지", NOW.minusHours(2), null);
        long newWarning = sanction(subject, "WARNING", "경고2", NOW.minusHours(1), null);
        JsonNode result = row(list(prefix, "SANCTIONED"), subject);
        assertThat(result.get("sanction").get("id").asString()).isEqualTo(Long.toString(ban));
        assertThat(result.get("sanctions").valueStream().map(node -> node.get("id").asString()).toList())
                .containsExactly(Long.toString(ban), Long.toString(newWarning), Long.toString(oldWarning));
    }

    @Test
    void repeatedWarningsChooseLatestThenHigherIdForSameTime() throws Exception {
        long subject = user("subject", null, "ACTIVE");
        sanction(subject, "WARNING", "이전 경고", NOW.minusDays(1), null);
        sanction(subject, "WARNING", "경고1", NOW, null);
        long newest = sanction(subject, "WARNING", "경고2", NOW, null);
        JsonNode result = row(list(prefix, null), subject);
        assertThat(result.get("sanction").get("id").asString()).isEqualTo(Long.toString(newest));
        assertThat(result.get("sanctions").size()).isEqualTo(3);
        assertThat(result.get("status").asString()).isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(ints = {168, 720})
    void mapsBackendSuspensionPeriodToFrontendType(int hours) throws Exception {
        long subject = user("subject", null, "SUSPENDED");
        OffsetDateTime start = NOW.withOffsetSameInstant(ZoneOffset.ofHours(9)).minusHours(1);
        sanction(subject, "SUSPEND", "기간 정지", start, start.plusHours(hours));
        JsonNode result = row(list(prefix, null), subject);
        assertThat(result.get("sanction").get("type").asString())
                .isEqualTo(hours == 168 ? "SUSPEND_7" : "SUSPEND_30");
    }

    @Test
    void expiryBoundaryIsReadOnlyAndDoesNotPretendAccountWasRestored() throws Exception {
        long subject = user("subject", null, "SUSPENDED");
        long id = sanction(subject, "SUSPEND", "경계", NOW.minusDays(7), NOW);
        jdbc.update("UPDATE users SET suspended_until=? WHERE id=?", NOW, subject);
        String before = jdbc.queryForObject("SELECT row_to_json(u)::text FROM users u WHERE id=?", String.class, subject);
        String history = jdbc.queryForObject("SELECT row_to_json(s)::text FROM user_sanctions s WHERE id=?", String.class, id);
        long logs = jdbc.queryForObject("SELECT count(*) FROM admin_action_logs", Long.class);
        given(clock.instant()).willReturn(NOW.minusSeconds(1).toInstant());
        assertThat(list(prefix, "SANCTIONED")).hasSize(1);
        given(clock.instant()).willReturn(NOW.toInstant());
        assertThat(list(prefix, "SANCTIONED")).isEmpty();
        JsonNode result = row(list(prefix, null), subject);
        assertThat(result.get("status").asString()).isEqualTo("SUSPENDED");
        assertThat(result.get("sanction").isNull()).isTrue();
        assertThat(result.get("sanctions").isEmpty()).isTrue();
        assertThat(jdbc.queryForObject("SELECT row_to_json(u)::text FROM users u WHERE id=?", String.class, subject)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT row_to_json(s)::text FROM user_sanctions s WHERE id=?", String.class, id)).isEqualTo(history);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_action_logs", Long.class)).isEqualTo(logs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WITHDRAWN_PENDING", "WITHDRAWN", "BANNED"})
    void keepsActualAccountStatesAndNicknameFallback(String state) throws Exception {
        long subject = user("subject", "  ", state);
        if (state.equals("WITHDRAWN")) {
            jdbc.update("UPDATE users SET deleted_at=? WHERE id=?", NOW, subject);
        }
        JsonNode result = row(list(prefix, null), subject);
        assertThat(result.get("status").asString()).isEqualTo(state);
        assertThat(result.get("nickname").asString()).isEqualTo(prefix + "subject");
        assertThat(result.get("sanction").isNull()).isTrue();
    }

    @Test
    void searchesNamesAndUsernamesCaseInsensitivelyAndTreatsSqlWildcardsLiterally() throws Exception {
        long id = user("subject", "검색%_이름", "ACTIVE");
        user("other", "다른 이름", "ACTIVE");
        assertThat(list("  " + prefix.toUpperCase() + "SUBJECT  ", null)).hasSize(1);
        assertThat(row(list("%_", null), id).get("nickname").asString()).isEqualTo("검색%_이름");
        assertThat(list("' OR 1=1 --", null)).isEmpty();
        assertThat(list(prefix + "nomatch", null)).isEmpty();
    }

    @Test
    void ordersByReportsThenIdWithoutPagination() throws Exception {
        long first = user("first", null, "ACTIVE");
        long second = user("second", null, "ACTIVE");
        long reported = user("reported", null, "ACTIVE");
        report(first, "USER", reported, null, true);
        assertThat(list(prefix, null)).extracting(node -> node.get("id").asString())
                .containsExactly(Long.toString(reported), Long.toString(second), Long.toString(first));
    }

    @Test
    void invalidFilterUsesCommonErrorContract() throws Exception {
        mvc.perform(request().param("filter", "ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/v1/admin/users").servletPath("/api/v1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void ordinaryMemberCannotRead() throws Exception {
        mvc.perform(get("/api/v1/admin/users").servletPath("/api/v1")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                new LoginUser(1L), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void roleAloneCannotReplaceAdminPrincipal() throws Exception {
        mvc.perform(get("/api/v1/admin/users").servletPath("/api/v1")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                "fake-admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private MockHttpServletRequestBuilder request() {
        return get("/api/v1/admin/users").servletPath("/api/v1")
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(new AdminPrincipal(adminId), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))));
    }

    private List<JsonNode> list(String query, String filter) throws Exception {
        MockHttpServletRequestBuilder request = request().param("q", query);
        if (filter != null) {
            request.param("filter", filter);
        }
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).valueStream().toList();
    }

    private static JsonNode row(List<JsonNode> rows, long id) {
        return rows.stream().filter(node -> node.get("id").asString().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    private long user(String suffix, String name, String state) {
        return jdbc.queryForObject("INSERT INTO users(username,display_name,status) VALUES (?,?,?) RETURNING id",
                Long.class, prefix + suffix, name, state);
    }

    private long feed(long userId, OffsetDateTime hiddenAt, OffsetDateTime deletedAt) {
        return jdbc.queryForObject("INSERT INTO feeds(user_id,content,hidden_at,deleted_at) VALUES (?,'내용',?,?) RETURNING id",
                Long.class, userId, hiddenAt, deletedAt);
    }

    private long sanction(long userId, String type, String reason, OffsetDateTime startedAt, OffsetDateTime until) {
        return jdbc.queryForObject("""
                INSERT INTO user_sanctions(user_id,type,reason,admin_id,created_at,suspended_until)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, userId, type, reason, adminId, startedAt, until);
    }

    private void revoke(long id) {
        jdbc.update("""
                UPDATE user_sanctions SET ended_at=?,end_reason='REVOKED',revoked_at=?,revoked_by_admin_id=? WHERE id=?
                """, NOW, NOW, adminId, id);
    }

    private void report(long reporterId, String type, long targetId, String snapshot, boolean resolved) {
        jdbc.update("""
                INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot,status,resolution_action,handled_by,handled_at)
                VALUES (?,?,?,'SPAM',?,?,?,?,?)
                """, reporterId, type, targetId, snapshot, resolved ? "RESOLVED" : "PENDING",
                resolved ? "DISMISS" : null, resolved ? adminId : null, resolved ? NOW : null);
    }
}
