package com.pickong.moderation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.error.BusinessException;
import com.pickong.moderation.application.port.out.ReportPersistencePort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportAction;
import com.pickong.moderation.domain.vo.ReportStatus;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 신고 저장과 대기 중복 인덱스. PostGIS 컨테이너가 필요합니다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReportPersistenceAdapterIntegrationTest {

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-25T03:00:00Z");

    @Autowired
    ReportPersistencePort reports;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows() {
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM users WHERE display_name = '저장테스트'");
    }

    @Test
    @DisplayName("대기 중복 인덱스가 적용되어 있다")
    void pendingUniqueIndexExists() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_reports_pending_reporter_target'",
                String.class);
        assertThat(definition).contains("UNIQUE").contains("reporter_id").contains("target_type").contains("target_id");
        assertThat(definition).containsIgnoringCase("WHERE").contains("PENDING");
    }

    @Test
    @DisplayName("새 신고는 대기 행으로 저장되고 기각해도 스냅샷은 그대로다")
    void saveKeepsTheSnapshotAfterDismiss() {
        long reporter = insertUser();
        String snapshot = "{\"text\":\"  첫째\\r\\n둘째  \",\"imageUrl\":null}";

        Report saved = reports.save(Report.pending(
                reporter, ReportTargetType.FEED, 41L, "SPAM", null, snapshot, CREATED_AT));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(saved.getResolutionAction()).isNull();
        assertThat(saved.getSnapshot()).isEqualTo(snapshot);
        assertThat(reports.existsPending(reporter, ReportTargetType.FEED, 41L)).isTrue();

        dismiss(saved.getId(), "유지", CREATED_AT.plusMinutes(5));

        Report stored = reports.findById(saved.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(stored.getResolutionAction()).isEqualTo(ReportAction.DISMISS);
        assertThat(stored.getSnapshot()).isEqualTo(snapshot);
        assertThat(reports.existsPending(reporter, ReportTargetType.FEED, 41L)).isFalse();
    }

    @Test
    @DisplayName("같은 대기 신고는 사유가 달라도 거절하고, 다른 회원과 다른 대상은 저장한다")
    void duplicatePendingIsRejected() {
        long reporter = insertUser();
        long other = insertUser();
        String firstSnapshot = "{\"text\":\"처음\"}";
        Report first = reports.save(Report.pending(
                reporter, ReportTargetType.COMMENT, 8L, "SPAM", "첫 내용", firstSnapshot, CREATED_AT));

        assertThatThrownBy(() -> reports.save(Report.pending(
                        reporter, ReportTargetType.COMMENT, 8L, "ABUSE", "다른 사유", "{\"text\":\"두번째\"}", CREATED_AT)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ModerationErrorCode.DUPLICATE_REPORT);
        assertThat(jdbc.queryForObject(
                "SELECT snapshot FROM reports WHERE id = ?", String.class, first.getId()))
                .isEqualTo(firstSnapshot);

        Report otherReporter = reports.save(Report.pending(
                other, ReportTargetType.COMMENT, 8L, "SPAM", null, "{\"text\":\"다른 회원\"}", CREATED_AT));
        Report otherTarget = reports.save(Report.pending(
                reporter, ReportTargetType.COMMENT, 9L, "SPAM", "", "{\"text\":\"다른 대상\"}", CREATED_AT));
        assertThat(otherReporter.getId()).isNotEqualTo(first.getId());
        assertThat(otherTarget.getId()).isNotEqualTo(first.getId());
    }

    @Test
    @DisplayName("처리된 신고가 있어도 같은 대상의 새 스냅샷으로 다시 저장한다")
    void resolvedReportCanBeFiledAgain() {
        long reporter = insertUser();
        String oldSnapshot = "{\"text\":\"예전\"}";
        Report oldReport = reports.save(Report.pending(
                reporter, ReportTargetType.FEED, 3L, "ETC", "상세", oldSnapshot, CREATED_AT));
        dismiss(oldReport.getId(), null, CREATED_AT.plusHours(1));

        String newSnapshot = "{\"text\":\"다시\"}";
        Report again = reports.save(Report.pending(
                reporter, ReportTargetType.FEED, 3L, "ETC", "다시 상세", newSnapshot, CREATED_AT.plusHours(2)));

        assertThat(again.getId()).isNotEqualTo(oldReport.getId());
        assertThat(reports.findById(oldReport.getId()).orElseThrow().getSnapshot()).isEqualTo(oldSnapshot);
        assertThat(reports.findById(oldReport.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(again.getSnapshot()).isEqualTo(newSnapshot);
        assertThat(again.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("없는 회원이나 빈 사유는 중복 신고 오류로 바꾸지 않는다")
    void otherDatabaseErrorsStayUnchanged() {
        assertThatThrownBy(() -> reports.save(Report.pending(
                        9_999_999L, ReportTargetType.FEED, 1L, "SPAM", null, "{}", CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> reports.save(Report.builder()
                        .reporterId(insertUser())
                        .targetType(ReportTargetType.FEED)
                        .targetId(1L)
                        .reason("   ")
                        .status(ReportStatus.PENDING)
                        .snapshot("{}")
                        .createdAt(CREATED_AT)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동시에 같은 대기 신고를 넣으면 하나만 저장된다")
    void concurrentPendingInsertsAllowOnlyOne() throws Exception {
        long reporter = insertUser();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        CopyOnWriteArrayList<Long> savedIds = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<BusinessException> duplicates = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        Runnable save = () -> {
            try {
                start.await();
                Report saved = reports.save(Report.pending(
                        reporter, ReportTargetType.FEED, 77L, "SPAM", null, "{\"text\":\"동시\"}", CREATED_AT));
                savedIds.add(saved.getId());
            } catch (BusinessException exception) {
                duplicates.add(exception);
            } catch (Throwable throwable) {
                unexpected.set(throwable);
            } finally {
                done.countDown();
            }
        };
        Thread first = new Thread(save);
        Thread second = new Thread(save);
        first.start();
        second.start();
        start.countDown();

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpected.get()).isNull();
        assertThat(savedIds).hasSize(1);
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.get(0).getErrorCode()).isEqualTo(ModerationErrorCode.DUPLICATE_REPORT);
    }

    private void dismiss(long reportId, String memo, OffsetDateTime at) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                reports.dismissIfPrimaryPending(reportId, 7L, memo, at));
    }

    private long insertUser() {
        String username = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject(
                "INSERT INTO users (username, display_name, email) VALUES (?, '저장테스트', ?) RETURNING id",
                Long.class, username, username + "@example.com");
    }
}
