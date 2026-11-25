package com.pickong.moderation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.error.BusinessException;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.moderation.application.dto.ReportCommand;
import com.pickong.moderation.application.dto.ReportReceipt;
import com.pickong.moderation.application.port.in.ReportUseCase;
import com.pickong.moderation.domain.ModerationErrorCode;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 접수 서비스가 저장한 FULL 스냅샷을 기존 관리자 파서가 읽는지 확인합니다. PostGIS 컨테이너가 필요합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportServiceIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    ReportUseCase reports;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteRows() {
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM comments WHERE content LIKE 'service-it-%'");
        jdbc.update("DELETE FROM feeds WHERE content LIKE 'service-it-%'");
        jdbc.update("DELETE FROM users WHERE display_name = '접수테스트'");
    }

    @Test
    @DisplayName("접수한 게시물 스냅샷은 둘째 줄이 바뀌면 원문이고 modified는 true다")
    void filedFeedSnapshotSurvivesTheAdminPreview() throws Exception {
        long author = insertUser();
        long reporter = insertUser();
        String original = "service-it-안녕하세요.\n부적절한 내용입니다.";
        long feed = insertFeed(author, original);

        ReportReceipt receipt = reports.report(new ReportCommand(reporter, ReportTargetType.FEED, feed, "SPAM", null));

        assertThat(receipt.status()).isEqualTo("PENDING");
        JsonNode stored = JSON.readTree(jdbc.queryForObject(
                "SELECT snapshot FROM reports WHERE id = ?", String.class, receipt.id()));
        assertThat(stored.get("text").asString()).isEqualTo(original);
        assertThat(stored.get("textScope").asString()).isEqualTo("FULL");
        assertThat(stored.get("reportedUserId").asLong()).isEqualTo(author);
        assertThat(stored.get("imageUrl").isNull()).isTrue();

        jdbc.update("UPDATE feeds SET content = ? WHERE id = ?", "service-it-안녕하세요.\n수정된 내용입니다.", feed);
        JsonNode item = find(pendingItems(), receipt.id());
        assertThat(item.get("target").get("snapshot").get("text").asString()).isEqualTo(original);
        assertThat(item.get("target").get("current").get("text").asString()).isEqualTo("service-it-안녕하세요.\n수정된 내용입니다.");
        assertThat(item.get("target").get("modified").asBoolean()).isTrue();
        assertThat(item.get("target").has("textScope")).isFalse();
        assertThat(receipt.toString()).doesNotContain(original);
    }

    @Test
    @DisplayName("같은 회원의 같은 대기 신고는 한 행만 남는다")
    void secondPendingReportIsRejected() {
        long author = insertUser();
        long reporter = insertUser();
        long feed = insertFeed(author, "service-it-한 번");
        reports.report(new ReportCommand(reporter, ReportTargetType.FEED, feed, "SPAM", null));

        assertThatThrownBy(() -> reports.report(new ReportCommand(reporter, ReportTargetType.FEED, feed, "ABUSE", "다른 사유")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ModerationErrorCode.DUPLICATE_REPORT);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM reports WHERE target_id = ?", Integer.class, feed);
        assertThat(count).isEqualTo(1);
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
                "INSERT INTO users (username, display_name, email) VALUES (?, '접수테스트', ?) RETURNING id",
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

    private static UsernamePasswordAuthenticationToken admin() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
