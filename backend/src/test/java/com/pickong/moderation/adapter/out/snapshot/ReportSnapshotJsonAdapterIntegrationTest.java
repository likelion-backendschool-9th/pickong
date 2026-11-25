package com.pickong.moderation.adapter.out.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.moderation.application.ReportSnapshots;
import com.pickong.moderation.application.dto.ReportTargetLookup;
import com.pickong.moderation.application.port.out.ReportSnapshotWriter;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 생성한 FULL 스냅샷을 기존 관리자 파서가 읽는지 확인합니다. PostGIS 컨테이너가 필요합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportSnapshotJsonAdapterIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReportSnapshotWriter writer;

    @BeforeEach
    void actualAdminForAudit() {
        jdbc.update("INSERT INTO admins(id,username,password_hash,role,status) VALUES (7,'snapshot-it-admin-7','test-only','OPERATOR','ACTIVE') ON CONFLICT(id) DO NOTHING");
        assertThat(jdbc.queryForObject("SELECT username FROM admins WHERE id=7", String.class)).isEqualTo("snapshot-it-admin-7");
    }

    @AfterEach
    void deleteRows() {
        jdbc.update("DELETE FROM admin_action_logs WHERE admin_id=7");
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM comments WHERE content LIKE 'snapshot-it-%'");
        jdbc.update("DELETE FROM feeds WHERE content LIKE 'snapshot-it-%' OR content LIKE 'snapshot-it-%' || chr(10) || '%'");
        jdbc.update("DELETE FROM users WHERE display_name = '스냅샷테스트'");
        jdbc.update("DELETE FROM admins WHERE id=7 AND username='snapshot-it-admin-7'");
    }

    @Test
    @DisplayName("게시물 둘째 줄이 바뀌면 생성한 스냅샷은 원문이고 modified는 true다")
    void generatedFeedSnapshotSurvivesAdminPreviewAndDismiss() throws Exception {
        long author = insertUser();
        long reporter = insertUser();
        String original = "snapshot-it-안녕하세요.\n부적절한 내용입니다.";
        long feed = insertFeed(author, original);
        String snapshot = writer.write(ReportSnapshots.from(new ReportTargetLookup(ReportTargetType.FEED, feed, author, original)));
        long reportId = insertPending(reporter, "FEED", feed, snapshot);
        jdbc.update("UPDATE feeds SET content = ? WHERE id = ?", "snapshot-it-안녕하세요.\n수정된 내용입니다.", feed);

        JsonNode item = find(pendingItems(), reportId);
        assertThat(item.get("target").get("snapshot").get("text").asString()).isEqualTo(original);
        assertThat(item.get("target").get("current").get("text").asString()).isEqualTo("snapshot-it-안녕하세요.\n수정된 내용입니다.");
        assertThat(item.get("target").get("modified").asBoolean()).isTrue();
        assertThat(item.get("target").has("textScope")).isFalse();
        assertThat(item.get("target").get("snapshot").has("textScope")).isFalse();

        mvc.perform(api(post("/api/v1/admin/reports/" + reportId + "/resolve"))
                        .with(authentication(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISMISS\",\"memo\":\"유지\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE id = ?", String.class, reportId)).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("댓글 본문이 같으면 생성한 스냅샷의 modified는 false다")
    void generatedCommentSnapshotMatchesTheCurrentBody() throws Exception {
        long feedAuthor = insertUser();
        long commentAuthor = insertUser();
        long reporter = insertUser();
        long feed = insertFeed(feedAuthor, "snapshot-it-상위");
        String original = "snapshot-it-첫 줄\n같은 끝";
        long comment = insertComment(feed, commentAuthor, original);
        String snapshot = writer.write(ReportSnapshots.from(
                new ReportTargetLookup(ReportTargetType.COMMENT, comment, commentAuthor, original)));
        long reportId = insertPending(reporter, "COMMENT", comment, snapshot);

        JsonNode item = find(pendingItems(), reportId);
        assertThat(item.get("target").get("snapshot").get("text").asString()).isEqualTo(original);
        assertThat(item.get("target").get("current").get("text").asString()).isEqualTo(original);
        assertThat(item.get("target").get("modified").asBoolean()).isFalse();
        assertThat(item.get("target").get("text").asString()).isEqualTo("snapshot-it-첫 줄");
    }

    private JsonNode pendingItems() throws Exception {
        var body = mvc.perform(api(get("/api/v1/admin/reports")).param("status", "PENDING").with(authentication(admin())))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(body.getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode items, long id) {
        String key = Long.toString(id);
        for (JsonNode item : items) {
            if (key.equals(item.get("id").asString())) {
                return item;
            }
        }
        throw new AssertionError("신고 " + id + "가 목록에 없습니다.");
    }

    private long insertUser() {
        String username = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject(
                "INSERT INTO users (username, display_name, email) VALUES (?, '스냅샷테스트', ?) RETURNING id",
                Long.class, username, username + "@example.com");
    }

    private long insertFeed(long userId, String content) {
        return jdbc.queryForObject(
                "INSERT INTO feeds (user_id, content) VALUES (?, ?) RETURNING id",
                Long.class, userId, content);
    }

    private long insertComment(long feedId, long userId, String content) {
        return jdbc.queryForObject(
                "INSERT INTO comments (feed_id, user_id, content) VALUES (?, ?, ?) RETURNING id",
                Long.class, feedId, userId, content);
    }

    private long insertPending(long reporterId, String targetType, long targetId, String snapshot) {
        return jdbc.queryForObject("""
                INSERT INTO reports (
                    reporter_id, target_type, target_id, reason, detail, status, snapshot, created_at
                ) VALUES (?, ?, ?, 'SPAM', '', 'PENDING', ?, ?)
                RETURNING id
                """, Long.class, reporterId, targetType, targetId, snapshot, OffsetDateTime.parse("2026-11-25T02:00:00Z"));
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private static UsernamePasswordAuthenticationToken admin() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L), null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
