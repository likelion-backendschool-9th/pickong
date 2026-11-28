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
import org.junit.jupiter.api.BeforeEach;
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

    private long adminId;

    @BeforeEach
    void activeAdminFixture() {
        adminId = jdbc.queryForObject("INSERT INTO admins(username,password_hash,role,status) VALUES (?,'test-only','OPERATOR','ACTIVE') RETURNING id",
                Long.class, "report-security-" + UUID.randomUUID());
    }

    @AfterEach
    void deleteRows() {
        jdbc.update("DELETE FROM admins WHERE id=?", adminId);
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

    @Test
    @DisplayName("볼 수 없는 글은 접수하지 않고, 게시물 작성자는 타인의 댓글 작성자를 저장한다")
    void unreadableTargetsAreNotFiledAndCommentAuthorIsStored() throws Exception {
        long feedAuthor = insertUser();
        long commentAuthor = insertUser();
        long reporter = insertUser();
        assertThat(feedAuthor).isNotEqualTo(commentAuthor);

        long followers = insertFeed(feedAuthor, "service-it-팔로워비밀", "FOLLOWERS");
        long privateFeed = insertFeed(feedAuthor, "service-it-비공개비밀", "PRIVATE");
        long hidden = insertFeed(feedAuthor, "service-it-숨김비밀", "PUBLIC");
        jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", hidden);
        long deleted = insertFeed(feedAuthor, "service-it-삭제비밀", "PUBLIC");
        jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", deleted);
        long visible = insertFeed(feedAuthor, "service-it-댓글상위");
        long hiddenComment = insertComment(visible, commentAuthor, "service-it-숨김댓글비밀");
        jdbc.update("UPDATE comments SET hidden_at = now() WHERE id = ?", hiddenComment);

        assertRejected(reporter, ReportTargetType.FEED, followers, ModerationErrorCode.REPORT_TARGET_NOT_FOUND, "팔로워비밀");
        assertRejected(reporter, ReportTargetType.FEED, privateFeed, ModerationErrorCode.REPORT_TARGET_NOT_FOUND, "비공개비밀");
        assertRejected(reporter, ReportTargetType.FEED, hidden, ModerationErrorCode.REPORT_TARGET_NOT_FOUND, "숨김비밀");
        assertRejected(reporter, ReportTargetType.FEED, deleted, ModerationErrorCode.REPORT_TARGET_NOT_FOUND, "삭제비밀");
        assertRejected(reporter, ReportTargetType.COMMENT, hiddenComment, ModerationErrorCode.REPORT_TARGET_NOT_FOUND, "숨김댓글비밀");
        assertRejected(feedAuthor, ReportTargetType.FEED, followers, ModerationErrorCode.SELF_REPORT, "팔로워비밀");

        long publicFeed = insertFeed(feedAuthor, "service-it-공개상위");
        long comment = insertComment(publicFeed, commentAuthor, "service-it-타인댓글");
        assertRejected(commentAuthor, ReportTargetType.COMMENT, comment, ModerationErrorCode.SELF_REPORT, "타인댓글");

        ReportReceipt receipt = reports.report(
                new ReportCommand(feedAuthor, ReportTargetType.COMMENT, comment, "ABUSE", null));

        assertThat(receipt.targetType()).isEqualTo("COMMENT");
        JsonNode stored = JSON.readTree(jdbc.queryForObject(
                "SELECT snapshot FROM reports WHERE id = ?", String.class, receipt.id()));
        assertThat(stored.get("text").asString()).isEqualTo("service-it-타인댓글");
        assertThat(stored.get("reportedUserId").asLong()).isEqualTo(commentAuthor);
        assertThat(stored.get("reportedUserId").asLong()).isNotEqualTo(feedAuthor);
        assertThat(stored.get("textScope").asString()).isEqualTo("FULL");
        Integer count = jdbc.queryForObject("SELECT count(*) FROM reports", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("팔로워는 FOLLOWERS 게시물과 그 댓글을 신고할 수 있고, 팔로우를 끊으면 다음 신고부터 404 (상세 열람과 같은 기준)")
    void followerCanReportFollowersOnlyContent() {
        long feedAuthor = insertUser();
        long commentAuthor = insertUser();
        long follower = insertUser();
        long stranger = insertUser();
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", follower, feedAuthor);
        long followersFeed = insertFeed(feedAuthor, "service-it-팔로워글", "FOLLOWERS");
        long comment = insertComment(followersFeed, commentAuthor, "service-it-팔로워글댓글");

        ReportReceipt feedReport = reports.report(new ReportCommand(follower, ReportTargetType.FEED, followersFeed, "SPAM", null));
        ReportReceipt commentReport = reports.report(new ReportCommand(follower, ReportTargetType.COMMENT, comment, "ABUSE", null));
        assertThat(feedReport.status()).isEqualTo("PENDING");
        assertThat(commentReport.status()).isEqualTo("PENDING");

        assertRejected(stranger, ReportTargetType.FEED, followersFeed, ModerationErrorCode.REPORT_TARGET_NOT_FOUND, "팔로워글");
        assertRejected(stranger, ReportTargetType.COMMENT, comment, ModerationErrorCode.REPORT_TARGET_NOT_FOUND, "팔로워글댓글");

        jdbc.update("DELETE FROM follows WHERE follower_id = ? AND followee_id = ?", follower, feedAuthor);
        jdbc.update("DELETE FROM reports WHERE reporter_id = ?", follower);
        assertRejected(follower, ReportTargetType.FEED, followersFeed, ModerationErrorCode.REPORT_TARGET_NOT_FOUND, "팔로워글");
    }

    @Test
    @DisplayName("팔로워가 신고한 FOLLOWERS 게시물 · 댓글은 신고 당시 본문이 스냅샷에 남고, 게시물을 고치거나 지워도 그대로다")
    void followerReportKeepsSnapshotAfterEditAndDelete() {
        long feedAuthor = insertUser();
        long follower = insertUser();
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", follower, feedAuthor);
        long feed = insertFeed(feedAuthor, "service-it-팔로워원문", "FOLLOWERS");
        long comment = insertComment(feed, feedAuthor, "service-it-팔로워댓글원문");

        ReportReceipt feedReport = reports.report(new ReportCommand(follower, ReportTargetType.FEED, feed, "SPAM", null));
        ReportReceipt commentReport = reports.report(new ReportCommand(follower, ReportTargetType.COMMENT, comment, "ABUSE", null));
        String feedSnapshot = snapshotOf(feedReport.id());
        String commentSnapshot = snapshotOf(commentReport.id());
        assertThat(feedSnapshot).contains("service-it-팔로워원문");
        assertThat(commentSnapshot).contains("service-it-팔로워댓글원문");

        jdbc.update("UPDATE feeds SET content = 'service-it-수정됨' WHERE id = ?", feed);
        assertThat(snapshotOf(feedReport.id())).isEqualTo(feedSnapshot);

        // 본인 삭제는 하드 삭제라 게시물과 댓글 행이 사라져도 신고와 스냅샷은 남아야 됨 (reports는 게시물을 FK로 가리키지 않음)
        jdbc.update("DELETE FROM feeds WHERE id = ?", feed);
        assertThat(snapshotOf(feedReport.id())).isEqualTo(feedSnapshot);
        assertThat(snapshotOf(commentReport.id())).isEqualTo(commentSnapshot);
    }

    private String snapshotOf(long reportId) {
        return jdbc.queryForObject("SELECT snapshot FROM reports WHERE id = ?", String.class, reportId);
    }

    private void assertRejected(
            long reporterId, ReportTargetType type, long targetId, ModerationErrorCode code, String secret) {
        assertThatThrownBy(() -> reports.report(new ReportCommand(reporterId, type, targetId, "SPAM", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> {
                    BusinessException exception = (BusinessException) thrown;
                    assertThat(exception.getErrorCode()).isEqualTo(code);
                    assertThat(exception.getMessage()).doesNotContain(secret);
                });
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
        return insertFeed(userId, content, "PUBLIC");
    }

    private long insertFeed(long userId, String content, String visibility) {
        return jdbc.queryForObject(
                "INSERT INTO feeds (user_id, content, visibility) VALUES (?, ?, ?) RETURNING id",
                Long.class, userId, content, visibility);
    }

    private long insertComment(long feedId, long userId, String content) {
        return jdbc.queryForObject(
                "INSERT INTO comments (feed_id, user_id, content) VALUES (?, ?, ?) RETURNING id",
                Long.class, feedId, userId, content);
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private UsernamePasswordAuthenticationToken admin() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(adminId), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
