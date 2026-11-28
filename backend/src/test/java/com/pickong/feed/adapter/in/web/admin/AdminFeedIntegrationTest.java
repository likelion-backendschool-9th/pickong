package com.pickong.feed.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.LoginUser;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 보안 체인과 Flyway를 적용한 격리 DB. 각 테스트의 데이터는 트랜잭션으로 롤백합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminFeedIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private String prefix;
    private long authorId;
    private long reporterId;
    private long adminId;

    @BeforeEach
    void fixtures() {
        prefix = "s9" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        authorId = user("author", "작성자", "ACTIVE");
        reporterId = user("reporter", "신고자", "ACTIVE");
        adminId = jdbc.queryForObject("""
                INSERT INTO admins(username,password_hash,role,status)
                VALUES (?,'test-only','OPERATOR','ACTIVE') RETURNING id
                """, Long.class, prefix);
    }

    @Test
    void returnsArrayWithUuidAndAdminContentContract() throws Exception {
        long feedId = feed(prefix + "제목\n둘째 줄 비공개 본문", "PUBLIC", false, false, NOW);
        UUID publicId = publicId(feedId);
        mvc.perform(request(prefix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(publicId.toString()))
                .andExpect(jsonPath("$[0].type").value("POST"))
                .andExpect(jsonPath("$[0].title").value(prefix + "제목"))
                .andExpect(jsonPath("$[0].author.id").value(Long.toString(authorId)))
                .andExpect(jsonPath("$[0].author.username").value(prefix + "author"))
                .andExpect(jsonPath("$[0].author.nickname").value("작성자"))
                .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[0].hidden").value(false))
                .andExpect(jsonPath("$[0].reportCount").value(0))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].imageUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].price").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].link").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].internalId").doesNotExist())
                .andExpect(jsonPath("$[0].content").doesNotExist())
                .andExpect(jsonPath("$[0].author.email").doesNotExist());
    }

    @Test
    void missingAndBlankSearchReturnUnpaginatedList() throws Exception {
        UUID id = publicId(feed(prefix + "목록", "PUBLIC", false, false, NOW));
        for (String query : List.of("", "  ")) {
            assertThat(list(query)).extracting(node -> node.get("id").asString()).contains(id.toString());
        }
        mvc.perform(adminRequest()).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "FOLLOWERS", "PRIVATE"})
    void includesHiddenAndEveryVisibilityForAdmin(String visibility) throws Exception {
        UUID visible = publicId(feed(prefix + "표시", visibility, false, false, NOW));
        UUID hidden = publicId(feed(prefix + "숨김", visibility, true, false, NOW));
        List<JsonNode> rows = list(prefix);
        assertThat(rows).hasSize(2);
        assertThat(row(rows, visible).get("visibility").asString()).isEqualTo(visibility);
        assertThat(row(rows, visible).get("hidden").asBoolean()).isFalse();
        assertThat(row(rows, hidden).get("visibility").asString()).isEqualTo(visibility);
        assertThat(row(rows, hidden).get("hidden").asBoolean()).isTrue();
    }

    @Test
    void excludesDeletedPostsEvenWhenHiddenOrReported() throws Exception {
        UUID live = publicId(feed(prefix + "정상", "PRIVATE", false, false, NOW));
        long deleted = feed(prefix + "삭제", "PUBLIC", true, true, NOW);
        report("FEED", deleted, true);
        assertThat(list(prefix)).extracting(node -> node.get("id").asString()).containsExactly(live.toString());
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM feeds WHERE id=?", OffsetDateTime.class, deleted)).isEqualTo(NOW);
    }

    @Test
    void countsOnlyFeedReportsWithoutJoinFanoutAndKeepsPendingAndResolvedHistory() throws Exception {
        long id = feed(prefix + "신고 대상", "PUBLIC", false, false, NOW);
        report("FEED", id, false);
        report("FEED", id, true);
        for (String type : List.of("USER", "COMMENT", "COLLECTION")) report(type, id, false);
        // feed_photos.media_file_id는 media_files FK(V25)라 실제 사진 행 2개를 만들어 연결함
        jdbc.update("""
                WITH m AS (INSERT INTO media_files(user_id,status) VALUES (?,'UPLOADED'),(?,'UPLOADED') RETURNING id)
                INSERT INTO feed_photos(feed_id,media_file_id,sort_order) SELECT ?, id, row_number() OVER (ORDER BY id) - 1 FROM m
                """, authorId, authorId, id);
        jdbc.update("INSERT INTO comments(feed_id,user_id,content) VALUES (?,?,'댓글1'),(?,?,'댓글2')", id, authorId, id, reporterId);
        List<JsonNode> rows = list(prefix);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("reportCount").asLong()).isEqualTo(2);
        assertThat(rows.getFirst().get("imageUrl").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE target_type='FEED' AND target_id=?", Long.class, id)).isEqualTo(2);
    }

    @Test
    void ordersByCreationTimeAndThenInternalIdRatherThanUuidOrUpdateTime() throws Exception {
        long old = feed(prefix + "old", "PUBLIC", false, false, NOW.minusHours(1));
        long first = feed(prefix + "first", "PUBLIC", false, false, NOW);
        long second = feed(prefix + "second", "PUBLIC", false, false, NOW);
        jdbc.update("UPDATE feeds SET public_id=?,updated_at=? WHERE id=?", UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"), NOW.plusDays(5), first);
        jdbc.update("UPDATE feeds SET public_id=? WHERE id=?", UUID.fromString("00000000-0000-4000-8000-000000000000"), second);
        assertThat(list(prefix)).extracting(node -> node.get("id").asString())
                .containsExactly(publicId(second).toString(), publicId(first).toString(), publicId(old).toString());
    }

    @Test
    void searchUsesFirstLineAndNicknameAndLiteralCaseInsensitiveMatching() throws Exception {
        UUID title = publicId(feed(prefix + "Travel%_\nsecond-line-only", "PUBLIC", false, false, NOW));
        assertThat(list("  TRAVEL  ")).extracting(node -> node.get("id").asString()).containsExactly(title.toString());
        assertThat(list("작성자")).extracting(node -> node.get("id").asString()).contains(title.toString());
        assertThat(list("%_")).extracting(node -> node.get("id").asString()).containsExactly(title.toString());
        assertThat(list("second-line-only")).isEmpty();
        assertThat(list(prefix + "author")).isEmpty();
        assertThat(list("' OR 1=1 --")).isEmpty();
        assertThat(list(prefix + "nomatch")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankAuthorNamesFallBackToUsername(String name) throws Exception {
        jdbc.update("UPDATE users SET display_name=? WHERE id=?", name, authorId);
        UUID id = publicId(feed(prefix + "글", "PUBLIC", false, false, NOW));
        JsonNode result = row(list(prefix + "author"), id);
        assertThat(result.get("author").get("nickname").asString()).isEqualTo(prefix + "author");
    }

    @Test
    void withdrawnAuthorDoesNotHideTheirRemainingPosts() throws Exception {
        jdbc.update("UPDATE users SET status='WITHDRAWN',deleted_at=?,display_name='탈퇴한 사용자',email=NULL WHERE id=?", NOW, authorId);
        UUID id = publicId(feed(prefix + "보존 글", "PUBLIC", false, false, NOW));
        assertThat(row(list(prefix), id).get("author").get("nickname").asString()).isEqualTo("탈퇴한 사용자");
    }

    @Test
    void largeAuthorIdIsAJsonStringAndPrivateAccountFieldsAreExcluded() throws Exception {
        long largeId = 9_007_199_254_740_993L;
        jdbc.update("INSERT INTO users(id,username,email,password_hash,bio) VALUES (?,?,?,'secret-hash','private-bio')",
                largeId, prefix + "large", "private@example.test");
        long id = feed(prefix + "큰 ID", "PUBLIC", false, false, NOW);
        jdbc.update("UPDATE feeds SET user_id=? WHERE id=?", largeId, id);
        JsonNode author = row(list(prefix), publicId(id)).get("author");
        assertThat(author.get("id").isString()).isTrue();
        assertThat(author.get("id").asString()).isEqualTo(Long.toString(largeId));
        assertThat(author.has("email")).isFalse();
        assertThat(author.has("passwordHash")).isFalse();
        assertThat(author.has("bio")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("titles")
    void representsOnlyTheFirstLineWithoutChangingStoredBody(String content, String title) throws Exception {
        long id = feed(content, "PUBLIC", false, false, NOW);
        assertThat(row(list(""), publicId(id)).get("title").asString()).isEqualTo(title);
        assertThat(jdbc.queryForObject("SELECT content FROM feeds WHERE id=?", String.class, id)).isEqualTo(content);
    }

    static Stream<Arguments> titles() {
        return Stream.of(Arguments.of(null, ""), Arguments.of("", ""), Arguments.of("제목\n본문", "제목"),
                Arguments.of("제목\r\n본문", "제목"), Arguments.of("제목\r본문", "제목"),
                Arguments.of("\n본문", ""), Arguments.of("  제목  \n본문", "  제목  "));
    }

    @Test
    void getDoesNotChangeVisibilityHiddenStateReportsCountersOrAuditLogs() throws Exception {
        long id = feed(prefix + "읽기 전용", "FOLLOWERS", true, false, NOW);
        report("FEED", id, false);
        jdbc.update("UPDATE feeds SET like_count=4,comment_count=7,bookmark_count=2 WHERE id=?", id);
        String before = jdbc.queryForObject("SELECT row_to_json(f)::text FROM feeds f WHERE id=?", String.class, id);
        String reports = jdbc.queryForObject("SELECT json_agg(r ORDER BY id)::text FROM reports r WHERE target_type='FEED' AND target_id=?", String.class, id);
        long logs = jdbc.queryForObject("SELECT count(*) FROM admin_action_logs", Long.class);
        list(prefix);
        list(prefix);
        assertThat(jdbc.queryForObject("SELECT row_to_json(f)::text FROM feeds f WHERE id=?", String.class, id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT json_agg(r ORDER BY id)::text FROM reports r WHERE target_type='FEED' AND target_id=?", String.class, id)).isEqualTo(reports);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_action_logs", Long.class)).isEqualTo(logs);
    }

    @Test
    void anonymousIs401AndMemberIs403() throws Exception {
        mvc.perform(get("/api/v1/admin/feeds").servletPath("/api/v1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/admin/feeds").servletPath("/api/v1")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(authorId), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void roleAloneIsNotAnAdminPrincipal() throws Exception {
        mvc.perform(get("/api/v1/admin/feeds").servletPath("/api/v1")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated("fake-admin", null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void emptyResultIs200WithEmptyArray() throws Exception {
        mvc.perform(request(prefix + "no-results")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    private MockHttpServletRequestBuilder adminRequest() {
        return get("/api/v1/admin/feeds").servletPath("/api/v1")
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(new AdminPrincipal(adminId), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))));
    }

    private MockHttpServletRequestBuilder request(String query) { return adminRequest().param("q", query); }

    private List<JsonNode> list(String query) throws Exception {
        return JSON.readTree(mvc.perform(request(query)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .valueStream().toList();
    }

    private static JsonNode row(List<JsonNode> rows, UUID id) {
        return rows.stream().filter(node -> node.get("id").asString().equals(id.toString())).findFirst().orElseThrow();
    }

    private long user(String suffix, String name, String state) {
        return jdbc.queryForObject("INSERT INTO users(username,display_name,status) VALUES (?,?,?) RETURNING id",
                Long.class, prefix + suffix, name, state);
    }

    private long feed(String content, String visibility, boolean hidden, boolean deleted, OffsetDateTime createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO feeds(user_id,content,visibility,hidden_at,deleted_at,created_at)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, authorId, content, visibility, hidden ? NOW : null, deleted ? NOW : null, createdAt);
    }

    private UUID publicId(long id) { return jdbc.queryForObject("SELECT public_id FROM feeds WHERE id=?", UUID.class, id); }

    private void report(String type, long targetId, boolean resolved) {
        jdbc.update("""
                INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot,status,resolution_action,handled_by,handled_at)
                VALUES (?,?,?,'SPAM','원본 스냅샷',?,?,?,?)
                """, reporterId, type, targetId, resolved ? "RESOLVED" : "PENDING",
                resolved ? "DISMISS" : null, resolved ? adminId : null, resolved ? NOW : null);
    }
}
