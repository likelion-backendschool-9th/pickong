package com.pickong.moderation.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.error.BusinessException;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.moderation.application.port.in.ReportAdminUseCase;
import com.pickong.moderation.application.port.out.ReportPersistencePort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.vo.ReportStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 신고 API와 reports 테이블. PostGIS 컨테이너가 필요합니다.
 * 브라우저 연동은 관리자 로그인이 없어 하지 않습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportAdminIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final OffsetDateTime OLDER = OffsetDateTime.parse("2026-11-24T01:00:00Z");
    private static final OffsetDateTime NEWER = OffsetDateTime.parse("2026-11-24T03:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReportAdminUseCase reports;

    @Autowired
    ReportPersistencePort persistence;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows() {
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM comments WHERE content LIKE 'report-it-%'");
        jdbc.update("DELETE FROM feeds WHERE content LIKE 'report-it-%'");
        jdbc.execute("DROP INDEX IF EXISTS uq_reports_test_one_resolved");
        jdbc.execute("ALTER TABLE reports DROP CONSTRAINT IF EXISTS ck_reports_test_reject_memo");
    }

    @Test
    @DisplayName("대기와 완료를 접수 시각 내림차순으로 나누고, 결과가 없으면 빈 배열이다")
    void listsByStatus() throws Exception {
        long reporter = insertUser("신고자", "reporter@example.com");
        long otherReporter = insertUser("다른 신고자", null);
        long author = insertUser("작성자", "author@example.com");
        long feed = insertFeed(author, "report-it-목록");
        long older = insertPending(otherReporter, "FEED", feed, "SPAM", "같은 사진", null, OLDER);
        long newer = insertPending(reporter, "FEED", feed, "ABUSE", "", null, NEWER);
        insertResolved(reporter, "USER", author, "ETC", OLDER.minusHours(1), 3L, "이전 기각");

        mvc.perform(api(get("/api/v1/admin/reports")).param("status", "NONE").with(authentication(admin())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_STATUS"));

        var pending = mvc.perform(api(get("/api/v1/admin/reports")).param("status", "PENDING").with(authentication(admin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pendingItems = JSON.readTree(pending.getResponse().getContentAsString());
        assertThat(ids(pendingItems)).containsExactly(Long.toString(newer), Long.toString(older));
        assertThat(pendingItems.get(0).get("targetType").asString()).isEqualTo("POST");
        assertThat(pendingItems.get(0).get("reporter").get("nickname").asString()).isEqualTo("신고자");
        assertThat(pendingItems.get(0).get("reportedUser").get("id").asString()).isEqualTo(Long.toString(author));
        assertThat(pendingItems.get(0).get("target").get("text").asString()).isEqualTo("report-it-목록");
        assertThat(pendingItems.get(0).get("target").get("link").asString()).startsWith("/posts/");
        assertThat(pendingItems.get(0).get("target").get("visibility").asString()).isEqualTo("LIVE");
        assertThat(pendingItems.get(0).get("target").get("snapshot").isNull()).isTrue();
        assertThat(pendingItems.get(0).get("target").get("current").get("text").asString()).isEqualTo("report-it-목록");
        assertThat(pendingItems.get(0).get("target").get("current").get("imageUrl").isNull()).isTrue();
        assertThat(pendingItems.get(0).get("target").get("modified").isNull()).isTrue();
        assertThat(pending.getResponse().getContentAsString()).doesNotContain("reporter@example.com");
        assertThat(pending.getResponse().getContentAsString()).doesNotContain("author@example.com");

        jdbc.update("DELETE FROM reports");
        mvc.perform(api(get("/api/v1/admin/reports")).param("status", "PENDING").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("기각은 선택한 신고만 닫고, 같은 대상의 다른 신고와 기존 처리 이력은 유지한다")
    void dismissesOnlyTheSelectedReport() throws Exception {
        long reporter = insertUser("신고자", null);
        long siblingReporter = insertUser("다른 신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-기각");
        long otherFeed = insertFeed(author, "report-it-다른대상");
        long primary = insertPending(reporter, "FEED", feed, "SPAM", "반복", null, NEWER);
        long sibling = insertPending(siblingReporter, "FEED", feed, "ABUSE", "", null, OLDER);
        long already = insertResolved(reporter, "FEED", feed, "SPAM", OLDER.minusHours(2), 3L, "기존 판단");
        long other = insertPending(reporter, "FEED", otherFeed, "SPAM", "", null, NEWER);

        mvc.perform(api(post("/api/v1/admin/reports/" + primary + "/resolve"))
                        .with(authentication(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISMISS\",\"memo\":\"  문제 없음  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution.action").value("DISMISS"))
                .andExpect(jsonPath("$.resolution.memo").value("문제 없음"))
                .andExpect(jsonPath("$.targetType").value("POST"));

        assertResolved(primary, 7L, "문제 없음");
        assertThat(statusOf(sibling)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT reason FROM reports WHERE id = ?", String.class, sibling)).isEqualTo("ABUSE");
        assertResolved(already, 3L, "기존 판단");
        assertThat(statusOf(other)).isEqualTo("PENDING");

        mvc.perform(api(post("/api/v1/admin/reports/" + primary + "/resolve"))
                        .with(authentication(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISMISS\",\"memo\":\"다시\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESOLVED"));
        assertResolved(primary, 7L, "문제 없음");
    }

    @Test
    @DisplayName("없는 신고, 잘못된 id, 숨김과 제재는 신고를 완료로 바꾸지 않는다")
    void rejectsWithoutResolving() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-거절");
        long id = insertPending(reporter, "FEED", feed, "SPAM", "", null, NEWER);

        mvc.perform(api(post("/api/v1/admin/reports/999999/resolve"))
                        .with(authentication(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISMISS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        mvc.perform(api(post("/api/v1/admin/reports/0/resolve"))
                        .with(authentication(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISMISS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mvc.perform(resolve(id, "{\"action\":\"HIDE_CONTENT\",\"memo\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESOLUTION_MEMO_REQUIRED"));
        mvc.perform(resolve(id, "{\"action\":\"HIDE_CONTENT\",\"memo\":\"광고\"}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("REPORT_ACTION_NOT_SUPPORTED"));
        mvc.perform(resolve(id, "{\"action\":\"SANCTION_USER\",\"memo\":\"반복\",\"sanctionType\":\"WEEK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SANCTION_TYPE"));
        mvc.perform(resolve(id, "{\"action\":\"SANCTION_USER\",\"memo\":\"반복\",\"sanctionType\":\"BAN\"}"))
                .andExpect(status().isNotImplemented());
        mvc.perform(resolve(id, "{\"action\":\"DISMISS\",\"sanctionType\":\"BAN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SANCTION_TYPE_NOT_ALLOWED"));
        mvc.perform(resolve(id, "{\"action\":\"DISMISS\",\"memo\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESOLUTION_MEMO_TOO_LONG"));

        assertThat(statusOf(id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT resolution_action FROM reports WHERE id = ?", String.class, id)).isNull();
    }

    @Test
    @DisplayName("삭제된 같은 게시물의 신고는 각자의 스냅샷을 유지한다")
    void deletedReportsKeepTheirOwnSnapshots() throws Exception {
        long reporter = insertUser("신고자", null);
        long otherReporter = insertUser("다른 신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-덮어쓰기");
        String olderSnapshot = """
                {"text":"욕설이 포함된 게시물","imageUrl":"https://example.com/old.jpg","reportedUserId":%d}
                """.formatted(author);
        String newerSnapshot = """
                {"text":"수정된 게시물","imageUrl":"https://example.com/new.jpg","reportedUserId":%d}
                """.formatted(author);
        long older = insertPending(otherReporter, "FEED", feed, "ABUSE", "", olderSnapshot, OLDER);
        long newer = insertPending(reporter, "FEED", feed, "SPAM", "", newerSnapshot, NEWER);
        jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", feed);

        var body = mvc.perform(api(get("/api/v1/admin/reports")).param("status", "PENDING").with(authentication(admin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = JSON.readTree(body.getResponse().getContentAsString());

        JsonNode olderItem = find(items, older);
        assertThat(olderItem.get("target").get("text").asString()).isEqualTo("욕설이 포함된 게시물");
        assertThat(olderItem.get("target").get("imageUrl").asString()).isEqualTo("https://example.com/old.jpg");
        assertThat(olderItem.get("target").get("snapshot").get("text").asString()).isEqualTo("욕설이 포함된 게시물");
        assertThat(olderItem.get("target").get("current").isNull()).isTrue();
        assertThat(olderItem.get("target").get("modified").isNull()).isTrue();
        assertThat(olderItem.get("target").get("visibility").asString()).isEqualTo("DELETED");
        JsonNode newerItem = find(items, newer);
        assertThat(newerItem.get("target").get("text").asString()).isEqualTo("수정된 게시물");
        assertThat(newerItem.get("target").get("imageUrl").asString()).isEqualTo("https://example.com/new.jpg");
        assertThat(newerItem.get("target").get("snapshot").get("text").asString()).isEqualTo("수정된 게시물");
        assertThat(newerItem.get("target").get("current").isNull()).isTrue();

        jdbc.update("UPDATE reports SET created_at = ? WHERE id = ?", NEWER, older);
        jdbc.update("UPDATE reports SET created_at = ? WHERE id = ?", OLDER, newer);
        var reordered = mvc.perform(api(get("/api/v1/admin/reports")).param("status", "PENDING").with(authentication(admin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode swapped = JSON.readTree(reordered.getResponse().getContentAsString());
        assertThat(ids(swapped)).containsExactly(Long.toString(older), Long.toString(newer));
        assertThat(find(swapped, older).get("target").get("text").asString()).isEqualTo("욕설이 포함된 게시물");
        assertThat(find(swapped, newer).get("target").get("text").asString()).isEqualTo("수정된 게시물");
    }

    @Test
    @DisplayName("현재 글이 있어도 신고 당시 스냅샷을 유지하고 수정 여부를 구분한다")
    void liveTargetKeepsSnapshotBesideCurrentContent() throws Exception {
        long reporter = insertUser("신고자", null);
        long otherReporter = insertUser("다른 신고자", null);
        long author = insertUser("작성자", null);
        long changed = insertFeed(author, "report-it-현재 본문\n둘째 줄");
        long unchanged = insertFeed(author, "report-it-같은글");
        long plain = insertFeed(author, "report-it-평문대상");
        String changedSnapshot = """
                {"text":"욕설이 포함된 게시물\\n이전 줄","imageUrl":"https://example.com/evidence.jpg","reportedUserId":%d}
                """.formatted(author);
        long changedReport = insertPending(reporter, "FEED", changed, "ABUSE", "", changedSnapshot, NEWER);
        long missingSnapshot = insertPending(otherReporter, "FEED", changed, "SPAM", "", null, OLDER);
        long sameReport = insertPending(
                reporter, "FEED", unchanged, "SPAM", "", "{\"text\":\"report-it-같은글\"}", NEWER);
        long plainReport = insertPending(reporter, "FEED", plain, "ETC", "", "평문 증거", NEWER);

        var body = mvc.perform(api(get("/api/v1/admin/reports")).param("status", "PENDING").with(authentication(admin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = JSON.readTree(body.getResponse().getContentAsString());

        JsonNode changedItem = find(items, changedReport);
        assertThat(changedItem.get("target").get("text").asString()).isEqualTo("욕설이 포함된 게시물");
        assertThat(changedItem.get("target").get("imageUrl").asString()).isEqualTo("https://example.com/evidence.jpg");
        assertThat(changedItem.get("target").get("link").asString()).startsWith("/posts/");
        assertThat(changedItem.get("target").get("visibility").asString()).isEqualTo("LIVE");
        assertThat(changedItem.get("target").get("snapshot").get("text").asString()).isEqualTo("욕설이 포함된 게시물\n이전 줄");
        assertThat(changedItem.get("target").get("snapshot").get("imageUrl").asString()).isEqualTo("https://example.com/evidence.jpg");
        assertThat(changedItem.get("target").get("current").get("text").asString()).isEqualTo("report-it-현재 본문\n둘째 줄");
        assertThat(changedItem.get("target").get("current").get("imageUrl").isNull()).isTrue();
        assertThat(changedItem.get("target").get("modified").asBoolean()).isTrue();

        JsonNode missingItem = find(items, missingSnapshot);
        assertThat(missingItem.get("target").get("text").asString()).isEqualTo("report-it-현재 본문");
        assertThat(missingItem.get("target").get("imageUrl").isNull()).isTrue();
        assertThat(missingItem.get("target").get("snapshot").isNull()).isTrue();
        assertThat(missingItem.get("target").get("current").get("text").asString()).isEqualTo("report-it-현재 본문\n둘째 줄");
        assertThat(missingItem.get("target").get("modified").isNull()).isTrue();

        JsonNode sameItem = find(items, sameReport);
        assertThat(sameItem.get("target").get("text").asString()).isEqualTo("report-it-같은글");
        assertThat(sameItem.get("target").get("current").get("text").asString()).isEqualTo("report-it-같은글");
        assertThat(sameItem.get("target").get("modified").asBoolean()).isFalse();

        JsonNode plainItem = find(items, plainReport);
        assertThat(plainItem.get("target").get("text").asString()).isEqualTo("평문 증거");
        assertThat(plainItem.get("target").get("snapshot").get("text").asString()).isEqualTo("평문 증거");
        assertThat(plainItem.get("target").get("current").get("text").asString()).isEqualTo("report-it-평문대상");
        assertThat(plainItem.get("target").get("modified").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("숨긴 콘텐츠는 현재 글을 보여주고, 없는 대상은 삭제로 보지 않는다")
    void hiddenContentStaysVisibleAndMissingTargetIsUnavailable() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long hiddenFeed = insertFeed(author, "report-it-숨긴 본문");
        long commentFeed = insertFeed(author, "report-it-댓글부모");
        jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", hiddenFeed);
        long comment = insertComment(commentFeed, author, "report-it-숨긴 댓글");
        jdbc.update("UPDATE comments SET hidden_at = now() WHERE id = ?", comment);
        String snapshot = """
                {"text":"숨기기 전 본문","imageUrl":"https://example.com/hidden.jpg","reportedUserId":%d}
                """.formatted(author);
        long hiddenReport = insertPending(reporter, "FEED", hiddenFeed, "ABUSE", "", snapshot, NEWER);
        long commentReport = insertPending(reporter, "COMMENT", comment, "ABUSE", "", null, NEWER);
        long missingReport = insertPending(reporter, "FEED", 9_876_543_210L, "SPAM", "", null, OLDER);

        var body = mvc.perform(api(get("/api/v1/admin/reports")).param("status", "PENDING").with(authentication(admin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = JSON.readTree(body.getResponse().getContentAsString());

        JsonNode hiddenItem = find(items, hiddenReport);
        assertThat(hiddenItem.get("target").get("text").asString()).isEqualTo("숨기기 전 본문");
        assertThat(hiddenItem.get("target").get("snapshot").get("text").asString()).isEqualTo("숨기기 전 본문");
        assertThat(hiddenItem.get("target").get("current").get("text").asString()).isEqualTo("report-it-숨긴 본문");
        assertThat(hiddenItem.get("target").get("visibility").asString()).isEqualTo("HIDDEN");
        assertThat(hiddenItem.get("target").get("link").isNull()).isTrue();
        assertThat(hiddenItem.get("target").get("modified").asBoolean()).isTrue();

        JsonNode commentItem = find(items, commentReport);
        assertThat(commentItem.get("target").get("text").asString()).isEqualTo("report-it-숨긴 댓글");
        assertThat(commentItem.get("target").get("current").get("text").asString()).isEqualTo("report-it-숨긴 댓글");
        assertThat(commentItem.get("target").get("visibility").asString()).isEqualTo("HIDDEN");
        assertThat(commentItem.get("target").get("link").isNull()).isTrue();

        JsonNode missingItem = find(items, missingReport);
        assertThat(missingItem.get("target").get("text").asString()).isEqualTo("확인할 수 없는 게시물");
        assertThat(missingItem.get("target").get("visibility").asString()).isEqualTo("UNAVAILABLE");
        assertThat(missingItem.get("target").get("current").isNull()).isTrue();
        assertThat(missingItem.get("target").get("snapshot").isNull()).isTrue();
        assertThat(missingItem.get("target").get("link").isNull()).isTrue();
    }

    @Test
    @DisplayName("삭제된 게시물은 스냅샷으로 보여주고 링크는 없으며, 컬렉션 테이블이 없어도 목록이 된다")
    void deletedTargetUsesSnapshot() throws Exception {
        long reporter = insertUser("신고자", null);
        long feed = insertFeed(reporter, "report-it-삭제전");
        String snapshot = """
                {"text":"삭제된 본문","imageUrl":"https://example.com/a.jpg","reportedUserId":%d}
                """.formatted(reporter);
        long deleted = insertPending(reporter, "FEED", feed, "SPAM", "상세", snapshot, NEWER);
        jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", feed);
        long collection = insertPending(reporter, "COLLECTION", 44L, "COPYRIGHT", "", null, OLDER);

        var body = mvc.perform(api(get("/api/v1/admin/reports")).param("status", "PENDING").with(authentication(admin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = JSON.readTree(body.getResponse().getContentAsString());
        JsonNode deletedItem = find(items, deleted);
        assertThat(deletedItem.get("target").get("text").asString()).isEqualTo("삭제된 본문");
        assertThat(deletedItem.get("target").get("imageUrl").asString()).isEqualTo("https://example.com/a.jpg");
        assertThat(deletedItem.get("target").get("link").isNull()).isTrue();
        assertThat(deletedItem.get("detail").asString()).isEqualTo("상세");
        JsonNode collectionItem = find(items, collection);
        assertThat(deletedItem.get("target").get("visibility").asString()).isEqualTo("DELETED");
        assertThat(deletedItem.get("target").get("current").isNull()).isTrue();
        assertThat(collectionItem.get("targetType").asString()).isEqualTo("COLLECTION");
        assertThat(collectionItem.get("target").get("text").asString()).isEqualTo("확인할 수 없는 컬렉션");
        assertThat(collectionItem.get("target").get("visibility").asString()).isEqualTo("UNAVAILABLE");
        assertThat(collectionItem.get("target").get("current").isNull()).isTrue();
        assertThat(collectionItem.get("target").get("link").isNull()).isTrue();
        assertThat(collectionItem.get("target").get("modified").isNull()).isTrue();

        reports.resolve(deleted, 7L, "DISMISS", null, null);
        assertThat(statusOf(deleted)).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("게시물 id와 같은 id의 다른 회원이 있어도 작성자는 user_id다")
    void reportedUserStaysTheAuthorWhenFeedIdMatchesAnotherUser() throws Exception {
        long author = insertUser("실제작성자", "author-map@example.com");
        long reporter = insertUser("신고자", "reporter-map@example.com");
        long shared = nextFreeId();
        long decoy = insertUserWithId(shared, "다른회원", "decoy-map@example.com");
        long feed = insertFeedWithId(shared, author, "report-it-작성자확인");
        assertThat(feed).isEqualTo(decoy).isNotEqualTo(author);

        String original = """
                {"text":"report-it-원문","imageUrl":null,"reportedUserId":%d,"textScope":"FULL"}
                """.formatted(author);
        long reportId = insertPending(reporter, "FEED", feed, "SPAM", "", original, NEWER);

        assertAuthor(items("PENDING"), reportId, author, "실제작성자");

        jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", feed);
        assertAuthor(items("PENDING"), reportId, author, "실제작성자");

        jdbc.update("UPDATE feeds SET hidden_at = null, deleted_at = now() WHERE id = ?", feed);
        JsonNode deleted = find(items("PENDING"), reportId);
        assertThat(deleted.get("target").get("visibility").asString()).isEqualTo("DELETED");
        assertThat(deleted.get("reportedUser").get("id").asString()).isEqualTo(Long.toString(author));
        assertThat(deleted.get("reportedUser").get("nickname").asString()).isEqualTo("실제작성자");
        assertThat(deleted.get("target").get("snapshot").get("text").asString()).isEqualTo("report-it-원문");
        assertThat(snapshotOf(reportId)).isEqualTo(original);

        long commentAuthor = insertUser("댓글작성자", "comment-map@example.com");
        long commentFeed = insertFeed(author, "report-it-댓글상위");
        long comment = insertComment(commentFeed, commentAuthor, "report-it-삭제댓글");
        assertThat(comment).isNotEqualTo(commentAuthor);
        String commentSnapshot = """
                {"text":"report-it-댓글원문","imageUrl":null,"reportedUserId":%d,"textScope":"FULL"}
                """.formatted(commentAuthor);
        long commentReport = insertPending(reporter, "COMMENT", comment, "ABUSE", "", commentSnapshot, OLDER);
        jdbc.update("UPDATE comments SET deleted_at = now() WHERE id = ?", comment);
        assertAuthor(items("PENDING"), commentReport, commentAuthor, "댓글작성자");
        assertThat(snapshotOf(commentReport)).isEqualTo(commentSnapshot);
        assertThat(snapshotOf(reportId)).isEqualTo(original);

        jdbc.update("DELETE FROM feeds WHERE id = ?", feed);
        JsonNode missing = find(items("PENDING"), reportId);
        assertThat(missing.get("target").get("visibility").asString()).isEqualTo("UNAVAILABLE");
        assertThat(missing.get("reportedUser").get("id").asString()).isEqualTo(Long.toString(author));
        assertThat(missing.get("reportedUser").get("nickname").asString()).isEqualTo("실제작성자");
        assertThat(snapshotOf(reportId)).isEqualTo(original);
    }

    @Test
    @DisplayName("같은 트랜잭션에서 다시 읽으면 기각 결과가 보이고, 저장이 실패하면 선택한 신고는 대기로 남는다")
    void sameTransactionSeesDismissalAndFailureRollsBack() {
        long reporter = insertUser("신고자", null);
        long siblingReporter = insertUser("다른 신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-롤백");
        long primary = insertPending(reporter, "FEED", feed, "SPAM", "", null, NEWER);
        long sibling = insertPending(siblingReporter, "FEED", feed, "SPAM", "", null, OLDER);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            persistence.dismissIfPrimaryPending(primary, 7L, "확인", NEWER.plusHours(1));
            assertThat(persistence.findById(primary).orElseThrow().getStatus()).isEqualTo(ReportStatus.RESOLVED);
            status.setRollbackOnly();
        });
        assertThat(statusOf(primary)).isEqualTo("PENDING");

        jdbc.execute("""
                ALTER TABLE reports ADD CONSTRAINT ck_reports_test_reject_memo
                    CHECK (resolution_memo IS DISTINCT FROM '하나')
                """);
        assertThatThrownBy(() -> reports.resolve(primary, 7L, "DISMISS", "하나", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ck_reports_test_reject_memo");
        assertThat(statusOf(primary)).isEqualTo("PENDING");
        assertThat(statusOf(sibling)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("같은 신고를 동시에 기각하면 한 번만 성공한다")
    void concurrentDismissOfSameReport() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-동시");
        long id = insertPending(reporter, "FEED", feed, "SPAM", "", null, NEWER);

        List<Object> outcomes = race(
                () -> reports.resolve(id, 7L, "DISMISS", "왼쪽", null),
                () -> reports.resolve(id, 8L, "DISMISS", "오른쪽", null));

        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(conflictCount(outcomes)).isEqualTo(1);
        String memo = jdbc.queryForObject("SELECT resolution_memo FROM reports WHERE id = ?", String.class, id);
        assertThat(memo).isIn("왼쪽", "오른쪽");
        assertThat(statusOf(id)).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("같은 대상의 서로 다른 신고를 동시에 기각하면 각자 처리된다")
    void concurrentDismissOfSameTarget() throws Exception {
        long reporter = insertUser("신고자", null);
        long rightReporter = insertUser("다른 신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-대상동시");
        long left = insertPending(reporter, "FEED", feed, "SPAM", "", null, NEWER);
        long right = insertPending(rightReporter, "FEED", feed, "ABUSE", "", null, OLDER);

        List<Object> outcomes = race(
                () -> reports.resolve(left, 7L, "DISMISS", "왼쪽", null),
                () -> reports.resolve(right, 8L, "DISMISS", "오른쪽", null));

        assertThat(successCount(outcomes)).isEqualTo(2);
        assertThat(conflictCount(outcomes)).isZero();
        assertResolved(left, 7L, "왼쪽");
        assertResolved(right, 8L, "오른쪽");
    }

    @Test
    @DisplayName("서로 다른 대상의 신고를 동시에 기각하면 둘 다 성공한다")
    void concurrentDismissOfDifferentTargets() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long leftFeed = insertFeed(author, "report-it-다른대상왼쪽");
        long rightFeed = insertFeed(author, "report-it-다른대상오른쪽");
        long left = insertPending(reporter, "FEED", leftFeed, "SPAM", "", null, NEWER);
        long right = insertPending(reporter, "FEED", rightFeed, "ABUSE", "", null, NEWER);

        List<Object> outcomes = race(
                () -> reports.resolve(left, 7L, "DISMISS", "왼쪽", null),
                () -> reports.resolve(right, 8L, "DISMISS", "오른쪽", null));

        assertThat(successCount(outcomes)).isEqualTo(2);
        assertThat(conflictCount(outcomes)).isZero();
        assertResolved(left, 7L, "왼쪽");
        assertResolved(right, 8L, "오른쪽");
    }

    @Test
    @DisplayName("기각 처리 중에 같은 대상으로 접수된 신고는 닫지 않는다")
    void reportInsertedDuringDismissStaysPending() {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-처리중접수");
        long primary = insertPending(reporter, "FEED", feed, "SPAM", "", null, NEWER);
        long[] arrived = new long[1];

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            persistence.dismissIfPrimaryPending(primary, 7L, "확인", NEWER.plusHours(1));
            arrived[0] = insertPending(
                    reporter, "FEED", feed, "ABUSE", "", "{\"text\":\"나중에 접수된 내용\"}", NEWER.plusMinutes(5));
        });

        assertResolved(primary, 7L, "확인");
        assertThat(statusOf(arrived[0])).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT resolution_action FROM reports WHERE id = ?", String.class, arrived[0])).isNull();
    }

    @Test
    @DisplayName("FULL 스냅샷 게시물은 첫 줄이 같아도 이후 본문 변경을 구분하고 전체 본문을 돌려줍니다")
    void fullFeedSnapshotComparesTheWholeBody() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        String current = "report-it-첫 줄\n바뀐 둘째 줄  ";
        String evidence = "report-it-첫 줄\n신고 당시 둘째 줄";
        long feed = insertFeed(author, current);
        String snapshot = jsonText(evidence, "FULL");
        long id = insertPending(reporter, "FEED", feed, "ABUSE", "", snapshot, NEWER);

        JsonNode item = find(pendingItems(), id);
        assertThat(item.get("target").get("text").asString()).isEqualTo("report-it-첫 줄");
        assertThat(item.get("target").get("snapshot").get("text").asString()).isEqualTo(evidence);
        assertThat(item.get("target").get("current").get("text").asString()).isEqualTo(current);
        assertThat(item.get("target").get("current").get("imageUrl").isNull()).isTrue();
        assertThat(item.get("target").get("modified").asBoolean()).isTrue();
        assertThat(item.get("target").has("textScope")).isFalse();

        reports.resolve(id, 7L, "DISMISS", null, null);
        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE id = ?", String.class, id)).isEqualTo(snapshot);
        JsonNode resolved = find(items("RESOLVED"), id);
        assertThat(resolved.get("target").get("snapshot").get("text").asString()).isEqualTo(evidence);
        assertThat(resolved.get("target").get("current").get("text").asString()).isEqualTo(current);
    }

    @Test
    @DisplayName("FULL 스냅샷 댓글은 첫 줄이 같아도 이후 본문 변경을 구분하고 전체 본문을 돌려줍니다")
    void fullCommentSnapshotComparesTheWholeBody() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-댓글부모");
        String current = "report-it-댓글 첫 줄\n바뀐 댓글 둘째 줄";
        String evidence = "report-it-댓글 첫 줄\n신고 당시 댓글";
        long comment = insertComment(feed, author, current);
        long id = insertPending(reporter, "COMMENT", comment, "ABUSE", "", jsonText(evidence, "FULL"), NEWER);

        JsonNode item = find(pendingItems(), id);
        assertThat(item.get("targetType").asString()).isEqualTo("COMMENT");
        assertThat(item.get("target").get("text").asString()).isEqualTo("report-it-댓글 첫 줄");
        assertThat(item.get("target").get("snapshot").get("text").asString()).isEqualTo(evidence);
        assertThat(item.get("target").get("current").get("text").asString()).isEqualTo(current);
        assertThat(item.get("target").get("modified").asBoolean()).isTrue();
        assertThat(item.get("target").get("link").asString()).startsWith("/posts/");
    }

    @Test
    @DisplayName("숨긴 FULL 본문은 비교하고 링크는 없으며, 삭제된 FULL 본문은 수정 여부를 판단하지 않습니다")
    void hiddenFullSnapshotComparesAndDeletedFullSnapshotDoesNot() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        String hiddenBody = "report-it-숨김 첫 줄\n현재 둘째";
        long hiddenFeed = insertFeed(author, hiddenBody);
        jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", hiddenFeed);
        long hiddenReport = insertPending(
                reporter, "FEED", hiddenFeed, "ABUSE", "",
                jsonText("report-it-숨김 첫 줄\n신고 당시 둘째", "FULL"), NEWER);
        long deletedFeed = insertFeed(author, "report-it-삭제될 본문\n둘째");
        String deletedSnapshot = jsonText("report-it-삭제될 본문\n신고 당시", "FULL");
        long deletedReport = insertPending(reporter, "FEED", deletedFeed, "SPAM", "", deletedSnapshot, OLDER);
        jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", deletedFeed);

        JsonNode items = pendingItems();
        JsonNode hiddenItem = find(items, hiddenReport);
        assertThat(hiddenItem.get("target").get("visibility").asString()).isEqualTo("HIDDEN");
        assertThat(hiddenItem.get("target").get("link").isNull()).isTrue();
        assertThat(hiddenItem.get("target").get("current").get("text").asString()).isEqualTo(hiddenBody);
        assertThat(hiddenItem.get("target").get("modified").asBoolean()).isTrue();

        JsonNode deletedItem = find(items, deletedReport);
        assertThat(deletedItem.get("target").get("visibility").asString()).isEqualTo("DELETED");
        assertThat(deletedItem.get("target").get("current").isNull()).isTrue();
        assertThat(deletedItem.get("target").get("snapshot").get("text").asString()).isEqualTo("report-it-삭제될 본문\n신고 당시");
        assertThat(deletedItem.get("target").get("modified").isNull()).isTrue();
    }

    @Test
    @DisplayName("회원 신고는 표시 이름만 비교하고, textScope가 FULL이어도 전체 본문으로 보지 않습니다")
    void userReportComparesDisplayNameOnly() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        String username = jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, author);
        String label = "작성자 (@" + username + ")";
        long secondReporter = insertUser("둘째 신고자", null);
        long thirdReporter = insertUser("셋째 신고자", null);
        long same = insertPending(reporter, "USER", author, "IMPERSONATION", "", jsonText(label, "FULL"), NEWER);
        long renamed = insertPending(
                secondReporter, "USER", author, "ETC", "", jsonText("다른 이름 (@" + username + ")", null), OLDER);
        long extraLine = insertPending(
                thirdReporter, "USER", author, "ABUSE", "", jsonText(label + "\n추가 설명", "FULL"), NEWER.minusMinutes(1));

        JsonNode items = pendingItems();
        assertThat(find(items, same).get("target").get("current").get("text").asString()).isEqualTo(label);
        assertThat(find(items, same).get("target").get("modified").asBoolean()).isFalse();
        assertThat(find(items, renamed).get("target").get("modified").asBoolean()).isTrue();
        assertThat(find(items, extraLine).get("target").get("modified").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("FULL 스냅샷은 빈 문자열과 누락된 본문을 구분합니다")
    void fullSnapshotDistinguishesBlankTextFromMissingText() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long emptyFeed = insertFeed(author, "report-it-비울본문");
        long nullFeed = insertFeed(author, "report-it-널본문");
        long filledFeed = insertFeed(author, "report-it-있는본문");
        jdbc.update("UPDATE feeds SET content = '' WHERE id = ?", emptyFeed);
        jdbc.update("UPDATE feeds SET content = NULL WHERE id = ?", nullFeed);
        try {
            long otherReporter = insertUser("다른 신고자", null);
            long bothEmpty = insertPending(reporter, "FEED", emptyFeed, "SPAM", "", jsonText("", "FULL"), NEWER);
            long onlySnapshotEmpty = insertPending(
                    reporter, "FEED", filledFeed, "SPAM", "", jsonText("", "FULL"), OLDER);
            long missingText = insertPending(
                    otherReporter, "FEED", emptyFeed, "ABUSE", "", jsonText(null, "FULL"), NEWER.minusMinutes(1));
            long currentMissing = insertPending(
                    reporter, "FEED", nullFeed, "ETC", "", jsonText("", "FULL"), OLDER.minusHours(1));

            JsonNode items = pendingItems();
            assertThat(find(items, bothEmpty).get("target").get("current").get("text").asString()).isEmpty();
            assertThat(find(items, bothEmpty).get("target").get("snapshot").get("text").asString()).isEmpty();
            assertThat(find(items, bothEmpty).get("target").get("modified").asBoolean()).isFalse();
            assertThat(find(items, onlySnapshotEmpty).get("target").get("modified").asBoolean()).isTrue();
            assertThat(find(items, missingText).get("target").get("snapshot").get("text").isNull()).isTrue();
            assertThat(find(items, missingText).get("target").get("modified").isNull()).isTrue();
            assertThat(find(items, currentMissing).get("target").get("current").get("text").isNull()).isTrue();
            assertThat(find(items, currentMissing).get("target").get("modified").isNull()).isTrue();
        } finally {
            jdbc.update("DELETE FROM feeds WHERE id IN (?, ?)", emptyFeed, nullFeed);
        }
    }

    /**
     * 하위 호환: 평문, textScope 없음 · null · FULL이 아닌 값은 첫 줄만 비교합니다.
     * 둘째 줄이 달라도 false인 것은 그 스냅샷을 전체 본문으로 보장할 수 없기 때문입니다.
     */
    @Test
    @DisplayName("기존 스냅샷은 첫 줄만 비교하고, 잘못된 스냅샷은 목록 조회를 막지 않습니다")
    void legacySnapshotsKeepFirstLineComparison() throws Exception {
        long[] reporters = new long[10];
        for (int i = 0; i < reporters.length; i++) {
            reporters[i] = insertUser("신고자" + i, null);
        }
        long author = insertUser("작성자", null);
        String current = "report-it-첫줄\n현재뒷줄";
        long feed = insertFeed(author, current);
        long plain = insertPending(reporters[0], "FEED", feed, "SPAM", "", "report-it-첫줄", NEWER);
        long noScope = insertPending(
                reporters[1], "FEED", feed, "ABUSE", "", "{\"text\":\"report-it-첫줄\\n옛뒷줄\"}", NEWER.minusMinutes(1));
        long nullScope = insertPending(
                reporters[2], "FEED", feed, "ETC", "",
                "{\"text\":\"report-it-첫줄\\n옛뒷줄\",\"textScope\":null}", NEWER.minusMinutes(2));
        long partial = insertPending(
                reporters[3], "FEED", feed, "SPAM", "", jsonText("report-it-첫줄\n옛뒷줄", "PARTIAL"), NEWER.minusMinutes(3));
        long paddedFull = insertPending(
                reporters[4], "FEED", feed, "SPAM", "", jsonText("report-it-첫줄\n옛뒷줄", "FULL "), NEWER.minusMinutes(4));
        long differentLine = insertPending(
                reporters[5], "FEED", feed, "ABUSE", "", "{\"text\":\"report-it-다른첫줄\\n현재뒷줄\"}", OLDER);
        long emptyFirstLine = insertPending(
                reporters[6], "FEED", feed, "ETC", "", "{\"text\":\"\\nreport-it-옛뒷줄\"}", OLDER.minusMinutes(1));
        long missingText = insertPending(reporters[7], "FEED", feed, "SPAM", "", jsonText(null, "FULL"), OLDER.minusMinutes(2));
        long invalidText = insertPending(
                reporters[8], "FEED", feed, "ABUSE", "", "{\"text\":1,\"textScope\":\"FULL\"}", OLDER.minusMinutes(3));
        long broken = insertPending(reporters[9], "FEED", feed, "SPAM", "", "{broken", OLDER.minusMinutes(4));

        JsonNode items = pendingItems();
        assertThat(items.size()).isEqualTo(10);
        assertThat(find(items, plain).get("target").get("modified").asBoolean()).isFalse();
        assertThat(find(items, plain).get("target").get("current").get("text").asString()).isEqualTo(current);
        assertThat(find(items, noScope).get("target").get("modified").asBoolean()).isFalse();
        assertThat(find(items, noScope).get("target").get("snapshot").get("text").asString()).isEqualTo("report-it-첫줄\n옛뒷줄");
        assertThat(find(items, nullScope).get("target").get("modified").asBoolean()).isFalse();
        assertThat(find(items, partial).get("target").get("modified").asBoolean()).isFalse();
        assertThat(find(items, paddedFull).get("target").get("modified").asBoolean()).isFalse();
        assertThat(find(items, differentLine).get("target").get("modified").asBoolean()).isTrue();
        assertThat(find(items, emptyFirstLine).get("target").get("modified").isNull()).isTrue();
        assertThat(find(items, missingText).get("target").get("modified").isNull()).isTrue();
        assertThat(find(items, invalidText).get("target").get("modified").isNull()).isTrue();
        assertThat(find(items, broken).get("target").get("modified").isNull()).isTrue();
        assertThat(find(items, broken).get("target").get("snapshot").get("text").asString()).isEqualTo("{broken");
    }

    private JsonNode pendingItems() throws Exception {
        return items("PENDING");
    }

    private JsonNode items(String status) throws Exception {
        var body = mvc.perform(api(get("/api/v1/admin/reports")).param("status", status).with(authentication(admin())))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(body.getResponse().getContentAsString());
    }

    /** text가 null이면 필드를 생략합니다. textScope가 null이면 그 필드도 생략합니다. */
    private static String jsonText(String text, String textScope) {
        StringBuilder builder = new StringBuilder("{");
        if (text != null) {
            builder.append("\"text\":").append(JSON.writeValueAsString(text));
        }
        if (textScope != null) {
            if (text != null) {
                builder.append(',');
            }
            builder.append("\"textScope\":").append(JSON.writeValueAsString(textScope));
        }
        builder.append('}');
        return builder.toString();
    }

    private MockHttpServletRequestBuilder resolve(long id, String json) {
        return api(post("/api/v1/admin/reports/" + id + "/resolve"))
                .with(authentication(admin()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private void assertResolved(long id, long adminId, String memo) {
        assertThat(statusOf(id)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("SELECT handled_by FROM reports WHERE id = ?", Long.class, id)).isEqualTo(adminId);
        assertThat(jdbc.queryForObject("SELECT resolution_action FROM reports WHERE id = ?", String.class, id)).isEqualTo("DISMISS");
        assertThat(jdbc.queryForObject("SELECT resolution_memo FROM reports WHERE id = ?", String.class, id)).isEqualTo(memo);
        assertThat(jdbc.queryForObject("SELECT handled_at FROM reports WHERE id = ?", OffsetDateTime.class, id)).isNotNull();
    }

    private String statusOf(long id) {
        return jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, id);
    }

    private void assertAuthor(JsonNode items, long reportId, long authorId, String nickname) {
        JsonNode item = find(items, reportId);
        assertThat(item.get("reportedUser").get("id").asString()).isEqualTo(Long.toString(authorId));
        assertThat(item.get("reportedUser").get("nickname").asString()).isEqualTo(nickname);
        assertThat(item.get("targetId").asString()).isNotEqualTo(item.get("reportedUser").get("id").asString());
    }

    private String snapshotOf(long reportId) {
        return jdbc.queryForObject("SELECT snapshot FROM reports WHERE id = ?", String.class, reportId);
    }

    private long nextFreeId() {
        return jdbc.queryForObject("""
                SELECT GREATEST(
                    (SELECT COALESCE(MAX(id), 0) FROM users),
                    (SELECT COALESCE(MAX(id), 0) FROM feeds),
                    (SELECT COALESCE(MAX(id), 0) FROM comments)
                ) + 1
                """, Long.class);
    }

    private long insertUserWithId(long id, String name, String email) {
        String username = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbc.update(
                "INSERT INTO users (id, username, display_name, email) VALUES (?, ?, ?, ?)",
                id, username, name, email);
        syncIdentity("users");
        return id;
    }

    private long insertFeedWithId(long id, long userId, String content) {
        jdbc.update("INSERT INTO feeds (id, user_id, content) VALUES (?, ?, ?)", id, userId, content);
        syncIdentity("feeds");
        return id;
    }

    private void syncIdentity(String table) {
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence(?, 'id'), (SELECT MAX(id) FROM " + table + "))",
                Long.class, table);
    }

    private long insertUser(String name, String email) {
        String username = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject(
                "INSERT INTO users (username, display_name, email) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, name, email);
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

    private long insertPending(long reporterId, String targetType, long targetId, String reason, String detail,
            String snapshot, OffsetDateTime createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO reports (
                    reporter_id, target_type, target_id, reason, detail, status, snapshot, created_at
                ) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)
                RETURNING id
                """, Long.class, reporterId, targetType, targetId, reason, detail, snapshot, createdAt);
    }

    private long insertResolved(long reporterId, String targetType, long targetId, String reason,
            OffsetDateTime createdAt, long adminId, String memo) {
        return jdbc.queryForObject("""
                INSERT INTO reports (
                    reporter_id, target_type, target_id, reason, status, resolution_action, resolution_memo,
                    handled_by, handled_at, created_at
                ) VALUES (?, ?, ?, ?, 'RESOLVED', 'DISMISS', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, reporterId, targetType, targetId, reason, memo, adminId, createdAt.plusMinutes(5), createdAt);
    }

    private static JsonNode find(JsonNode items, long id) {
        for (JsonNode item : items) {
            if (Long.toString(id).equals(item.get("id").asString())) {
                return item;
            }
        }
        throw new AssertionError("응답에 신고 " + id + " 가 없습니다.");
    }

    private static List<String> ids(JsonNode items) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : items) {
            ids.add(item.get("id").asString());
        }
        return ids;
    }

    private List<Object> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> left = pool.submit(() -> runRace(ready, start, first));
            Future<Object> right = pool.submit(() -> runRace(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            outcomes.add(left.get(10, TimeUnit.SECONDS));
            outcomes.add(right.get(10, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Object runRace(CountDownLatch ready, CountDownLatch start, Runnable action) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException("동시 실행을 시작하지 못했습니다.");
            }
            action.run();
            return "ok";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return exception;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private static long successCount(List<Object> outcomes) {
        return outcomes.stream().filter("ok"::equals).count();
    }

    private static long conflictCount(List<Object> outcomes) {
        return outcomes.stream()
                .filter(BusinessException.class::isInstance)
                .map(BusinessException.class::cast)
                .filter(exception -> exception.getErrorCode() == ModerationErrorCode.ALREADY_RESOLVED)
                .count();
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private static UsernamePasswordAuthenticationToken admin() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
