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
    }

    @Test
    @DisplayName("대기와 완료를 접수 시각 내림차순으로 나누고, 결과가 없으면 빈 배열이다")
    void listsByStatus() throws Exception {
        long reporter = insertUser("신고자", "reporter@example.com");
        long author = insertUser("작성자", "author@example.com");
        long feed = insertFeed(author, "report-it-목록");
        long older = insertPending(reporter, "FEED", feed, "SPAM", "같은 사진", null, OLDER);
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
        assertThat(pending.getResponse().getContentAsString()).doesNotContain("reporter@example.com");
        assertThat(pending.getResponse().getContentAsString()).doesNotContain("author@example.com");

        jdbc.update("DELETE FROM reports");
        mvc.perform(api(get("/api/v1/admin/reports")).param("status", "PENDING").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("기각은 같은 대상의 대기 신고를 함께 닫고, 이미 처리된 이력은 유지한다")
    void dismissCascadesPendingOnly() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-기각");
        long otherFeed = insertFeed(author, "report-it-다른대상");
        long primary = insertPending(reporter, "FEED", feed, "SPAM", "반복", null, NEWER);
        long sibling = insertPending(reporter, "FEED", feed, "SPAM", "", null, OLDER);
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
        assertResolved(sibling, 7L, "문제 없음");
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
        assertThat(collectionItem.get("targetType").asString()).isEqualTo("COLLECTION");
        assertThat(collectionItem.get("target").get("text").asString()).isEqualTo("삭제된 컬렉션");
        assertThat(collectionItem.get("target").get("link").isNull()).isTrue();

        reports.resolve(deleted, 7L, "DISMISS", null, null);
        assertThat(statusOf(deleted)).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("같은 트랜잭션에서 다시 읽으면 기각 결과가 보이고, 일괄 기각이 중간에 실패하면 전부 대기이다")
    void sameTransactionSeesDismissalAndFailureRollsBack() {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-롤백");
        long primary = insertPending(reporter, "FEED", feed, "SPAM", "", null, NEWER);
        long sibling = insertPending(reporter, "FEED", feed, "SPAM", "", null, OLDER);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            persistence.dismissIfPrimaryPending(primary, 7L, "확인", NEWER.plusHours(1));
            assertThat(persistence.findById(primary).orElseThrow().getStatus()).isEqualTo(ReportStatus.RESOLVED);
            status.setRollbackOnly();
        });
        assertThat(statusOf(primary)).isEqualTo("PENDING");

        jdbc.execute("""
                CREATE UNIQUE INDEX uq_reports_test_one_resolved
                    ON reports (target_type, target_id)
                    WHERE status = 'RESOLVED'
                """);
        assertThatThrownBy(() -> reports.resolve(primary, 7L, "DISMISS", "하나", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("uq_reports_test_one_resolved");
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
    @DisplayName("같은 대상의 서로 다른 신고를 동시에 기각해도 처리 이력은 하나로 맞는다")
    void concurrentDismissOfSameTarget() throws Exception {
        long reporter = insertUser("신고자", null);
        long author = insertUser("작성자", null);
        long feed = insertFeed(author, "report-it-대상동시");
        long left = insertPending(reporter, "FEED", feed, "SPAM", "", null, NEWER);
        long right = insertPending(reporter, "FEED", feed, "ABUSE", "", null, OLDER);

        List<Object> outcomes = race(
                () -> reports.resolve(left, 7L, "DISMISS", "왼쪽", null),
                () -> reports.resolve(right, 8L, "DISMISS", "오른쪽", null));

        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(conflictCount(outcomes)).isEqualTo(1);
        String leftMemo = jdbc.queryForObject("SELECT resolution_memo FROM reports WHERE id = ?", String.class, left);
        String rightMemo = jdbc.queryForObject("SELECT resolution_memo FROM reports WHERE id = ?", String.class, right);
        assertThat(leftMemo).isEqualTo(rightMemo);
        assertThat(leftMemo).isIn("왼쪽", "오른쪽");
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
