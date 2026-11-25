package com.pickong.moderation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import com.pickong.moderation.domain.vo.ReportTargetType;
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

/** 회원 신고 HTTP가 서버 본문을 저장하는지 확인합니다. PostGIS 컨테이너가 필요합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportControllerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteRows() {
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM feeds WHERE content LIKE 'report-api-%'");
        jdbc.update("DELETE FROM user_terms_agreements WHERE user_id IN (SELECT id FROM users WHERE display_name = '접수API테스트')");
        jdbc.update("DELETE FROM users WHERE display_name = '접수API테스트'");
    }

    @Test
    @DisplayName("접수는 201이고 스냅샷은 서버 본문이며 응답에는 원문이 없다")
    void createsAReportFromTheServerBody() throws Exception {
        long author = insertUser();
        long reporter = insertUser();
        agree(reporter);
        String original = "report-api-첫째 줄\n둘째 줄";
        long feed = insertFeed(author, original);

        var response = mvc.perform(api(post("/api/v1/reports"))
                        .with(authentication(member(reporter)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"POST","targetId":%d,"reason":"SPAM","detail":"광고",
                                 "text":"조작된 본문","reportedUserId":1}
                                """.formatted(feed)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("POST"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.snapshot").doesNotExist())
                .andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("조작된 본문").doesNotContain(original);

        long reportId = JSON.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        JsonNode stored = JSON.readTree(jdbc.queryForObject(
                "SELECT snapshot FROM reports WHERE id = ?", String.class, reportId));
        assertThat(stored.get("text").asString()).isEqualTo(original);
        assertThat(stored.get("textScope").asString()).isEqualTo("FULL");
        assertThat(stored.get("reportedUserId").asLong()).isEqualTo(author);
        assertThat(jdbc.queryForObject("SELECT target_type FROM reports WHERE id = ?", String.class, reportId))
                .isEqualTo(ReportTargetType.FEED.name());
    }

    @Test
    @DisplayName("없는 대상, 자기 글, 중복 신고, 기타 사유 누락은 각 오류로 거절한다")
    void rejectsInvalidReports() throws Exception {
        long author = insertUser();
        agree(author);
        long feed = insertFeed(author, "report-api-내 글");

        mvc.perform(report(author, "{\"targetType\":\"POST\",\"targetId\":9876543210,\"reason\":\"SPAM\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
        mvc.perform(report(author, "{\"targetType\":\"POST\",\"targetId\":" + feed + ",\"reason\":\"ABUSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_REPORT"));
        mvc.perform(report(author, "{\"targetType\":\"POST\",\"targetId\":" + feed + ",\"reason\":\"ETC\",\"detail\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_DETAIL_REQUIRED"));

        long reporter = insertUser();
        agree(reporter);
        long publicFeed = insertFeed(author, "report-api-공개");
        mvc.perform(report(reporter, "{\"targetType\":\"FEED\",\"targetId\":" + publicFeed + ",\"reason\":\"SPAM\"}"))
                .andExpect(status().isCreated());
        mvc.perform(report(reporter, "{\"targetType\":\"POST\",\"targetId\":" + publicFeed + ",\"reason\":\"COPYRIGHT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REPORT"));
        Integer count = jdbc.queryForObject("SELECT count(*) FROM reports WHERE target_id = ?", Integer.class, publicFeed);
        assertThat(count).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder report(long reporterId, String json) {
        return api(post("/api/v1/reports"))
                .with(authentication(member(reporterId)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
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
                "INSERT INTO users (username, display_name, email) VALUES (?, '접수API테스트', ?) RETURNING id",
                Long.class, username, username + "@example.com");
    }

    private long insertFeed(long userId, String content) {
        return jdbc.queryForObject(
                "INSERT INTO feeds (user_id, content) VALUES (?, ?) RETURNING id",
                Long.class, userId, content);
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private static UsernamePasswordAuthenticationToken member(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
