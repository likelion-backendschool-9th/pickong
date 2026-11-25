package com.pickong.moderation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.LoginUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

/**
 * 회원 접수로 만든 FULL 스냅샷이 관리자 조회와 기각 뒤에도 신고 시점의 원문인지 확인합니다.
 * PostGIS 컨테이너가 필요합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportIntakeFlowIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteRows() {
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM comments WHERE content LIKE 'flow-it-%'");
        jdbc.update("DELETE FROM feeds WHERE content LIKE 'flow-it-%'");
        jdbc.update("""
                DELETE FROM user_terms_agreements
                 WHERE user_id IN (SELECT id FROM users WHERE display_name = '접수흐름테스트')
                """);
        jdbc.update("DELETE FROM users WHERE display_name = '접수흐름테스트'");
    }

    @Test
    @DisplayName("댓글 신고는 둘째 줄 수정과 기각 뒤에도 접수 당시 전체 본문을 유지한다")
    void commentReportKeepsTheOriginalAfterEditAndDismiss() throws Exception {
        long author = insertUser();
        long reporter = insertUser();
        agree(reporter);
        long feed = insertFeed(author, "flow-it-상위");
        String original = "flow-it-첫 줄\n  둘째 줄  ";
        long comment = insertComment(feed, author, original);

        long reportId = file(reporter, "COMMENT", comment, "ABUSE", null);
        JsonNode same = adminItem("PENDING", reportId);
        assertThat(same.get("target").get("snapshot").get("text").asString()).isEqualTo(original);
        assertThat(same.get("target").get("current").get("text").asString()).isEqualTo(original);
        assertThat(same.get("target").get("modified").asBoolean()).isFalse();
        assertThat(same.get("target").get("text").asString()).isEqualTo("flow-it-첫 줄");
        assertThat(same.toString()).doesNotContain("textScope");

        jdbc.update("UPDATE comments SET content = ? WHERE id = ?", "flow-it-첫 줄\n바뀐 끝", comment);
        JsonNode changed = adminItem("PENDING", reportId);
        assertThat(changed.get("target").get("snapshot").get("text").asString()).isEqualTo(original);
        assertThat(changed.get("target").get("current").get("text").asString()).isEqualTo("flow-it-첫 줄\n바뀐 끝");
        assertThat(changed.get("target").get("modified").asBoolean()).isTrue();

        mvc.perform(api(post("/api/v1/admin/reports/" + reportId + "/resolve"))
                        .with(authentication(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISMISS\",\"memo\":\"유지\"}"))
                .andExpect(status().isOk());
        JsonNode stored = JSON.readTree(jdbc.queryForObject(
                "SELECT snapshot FROM reports WHERE id = ?", String.class, reportId));
        assertThat(stored.get("text").asString()).isEqualTo(original);
        assertThat(stored.get("textScope").asString()).isEqualTo("FULL");
        assertThat(stored.get("reportedUserId").asLong()).isEqualTo(author);
        assertThat(adminItem("RESOLVED", reportId).get("target").get("snapshot").get("text").asString()).isEqualTo(original);
    }

    @Test
    @DisplayName("먼저 신고한 회원과 수정 뒤에 신고한 회원은 각자 시점의 본문을 유지한다")
    void reportersKeepTheBodyFromTheirOwnMoment() throws Exception {
        long author = insertUser();
        long firstReporter = insertUser();
        long secondReporter = insertUser();
        agree(firstReporter);
        agree(secondReporter);
        String original = "flow-it-처음\n원문 끝";
        String edited = "flow-it-처음\n수정 끝";
        long feed = insertFeed(author, original);

        long first = file(firstReporter, "POST", feed, "SPAM", null);
        jdbc.update("UPDATE feeds SET content = ? WHERE id = ?", edited, feed);
        long second = file(secondReporter, "POST", feed, "ABUSE", "나중 신고");

        JsonNode firstItem = adminItem("PENDING", first);
        JsonNode secondItem = adminItem("PENDING", second);
        assertThat(firstItem.get("target").get("snapshot").get("text").asString()).isEqualTo(original);
        assertThat(firstItem.get("target").get("modified").asBoolean()).isTrue();
        assertThat(secondItem.get("target").get("snapshot").get("text").asString()).isEqualTo(edited);
        assertThat(secondItem.get("target").get("current").get("text").asString()).isEqualTo(edited);
        assertThat(secondItem.get("target").get("modified").asBoolean()).isFalse();

        jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", feed);
        JsonNode firstDeleted = adminItem("PENDING", first);
        JsonNode secondDeleted = adminItem("PENDING", second);
        assertThat(firstDeleted.get("target").get("snapshot").get("text").asString()).isEqualTo(original);
        assertThat(secondDeleted.get("target").get("snapshot").get("text").asString()).isEqualTo(edited);
        assertThat(firstDeleted.get("target").get("current").isNull()).isTrue();
        assertThat(secondDeleted.get("target").get("current").isNull()).isTrue();
        assertThat(firstDeleted.get("target").get("modified").isNull()).isTrue();
        assertThat(secondDeleted.get("target").get("modified").isNull()).isTrue();
    }

    private long file(long reporterId, String targetType, long targetId, String reason, String detail) throws Exception {
        String detailJson = detail == null ? "" : ",\"detail\":" + JSON.writeValueAsString(detail);
        var response = mvc.perform(api(post("/api/v1/reports"))
                        .with(authentication(member(reporterId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"%s","targetId":%d,"reason":"%s"%s,"text":"조작된 본문"}
                                """.formatted(targetType, targetId, reason, detailJson)))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("조작된 본문").doesNotContain("textScope");
        return JSON.readTree(response.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode adminItem(String status, long reportId) throws Exception {
        var body = mvc.perform(api(get("/api/v1/admin/reports")).param("status", status).with(authentication(admin())))
                .andExpect(status().isOk())
                .andReturn();
        String key = Long.toString(reportId);
        for (JsonNode item : JSON.readTree(body.getResponse().getContentAsString())) {
            if (key.equals(item.get("id").asString())) {
                return item;
            }
        }
        throw new AssertionError("신고 " + reportId + "가 " + status + " 목록에 없습니다.");
    }

    private void agree(long userId) {
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, userId);
    }

    private long insertUser() {
        String username = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject(
                "INSERT INTO users (username, display_name, email) VALUES (?, '접수흐름테스트', ?) RETURNING id",
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

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private static UsernamePasswordAuthenticationToken member(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static UsernamePasswordAuthenticationToken admin() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
