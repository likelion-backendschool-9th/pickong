package com.pickong.moderation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.error.ErrorCode;
import com.pickong.moderation.application.dto.ReportCommand;
import com.pickong.moderation.application.dto.ReportDismissal;
import com.pickong.moderation.application.dto.ReportReceipt;
import com.pickong.moderation.application.dto.ReportTargetLookup;
import com.pickong.moderation.application.port.out.ReportPersistencePort;
import com.pickong.moderation.application.port.out.ReportSnapshotWriter;
import com.pickong.moderation.application.port.out.ReportTargetLookupPort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.model.ReportSnapshot;
import com.pickong.moderation.domain.vo.ReportStatus;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 회원 신고 접수. 조회와 저장은 가짜라 클라이언트가 준 본문을 스냅샷으로 쓰지 않는지만 확인합니다. */
class ReportServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    private static final String ORIGINAL = "  첫째 줄\r\n둘째 줄  ";

    private final FakeTargets targets = new FakeTargets();
    private final FakeReports reports = new FakeReports();
    private final FakeWriter writer = new FakeWriter();
    private final ReportService service = new ReportService(
            targets, reports, writer, Clock.fixed(Instant.parse("2026-11-25T03:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void reportableFeed() {
        targets.found = new ReportTargetLookup(ReportTargetType.FEED, 41L, 9L, ORIGINAL);
    }

    @Test
    @DisplayName("서버가 읽은 게시물 본문으로 대기 신고를 저장한다")
    void reportsTheFeedTheServerRead() {
        ReportReceipt receipt = service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, " SPAM ", "  광고  "));

        assertThat(receipt.id()).isEqualTo(1L);
        assertThat(receipt.status()).isEqualTo("PENDING");
        assertThat(receipt.reason()).isEqualTo("SPAM");
        assertThat(receipt.createdAt()).isEqualTo(NOW);
        assertThat(receipt.toString()).doesNotContain(ORIGINAL);
        Report saved = reports.saved.getFirst();
        assertThat(saved.getReporterId()).isEqualTo(2L);
        assertThat(saved.getDetail()).isEqualTo("광고");
        assertThat(saved.getResolutionAction()).isNull();
        assertThat(saved.getSnapshot()).isEqualTo("FULL:9:" + ORIGINAL);
        assertThat(writer.last.text()).isEqualTo(ORIGINAL);
        assertThat(writer.last.imageUrl()).isNull();
        assertThat(writer.last.textScope()).isEqualTo("FULL");
        assertThat(targets.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글도 조회한 본문 원문으로 별도 스냅샷을 남긴다")
    void reportsACommentWithoutChangingTheEarlierSnapshot() {
        service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, "SPAM", null));
        String firstSnapshot = reports.saved.getFirst().getSnapshot();
        targets.found = new ReportTargetLookup(ReportTargetType.COMMENT, 8L, 4L, "");

        ReportReceipt comment = service.report(new ReportCommand(3L, ReportTargetType.COMMENT, 8L, "ABUSE", null));

        assertThat(comment.targetType()).isEqualTo("COMMENT");
        assertThat(reports.saved.getFirst().getSnapshot()).isEqualTo(firstSnapshot);
        assertThat(reports.saved.get(1).getSnapshot()).isEqualTo("FULL:4:");
        assertThat(writer.last.text()).isEmpty();
    }

    @Test
    @DisplayName("처리된 뒤의 재신고는 새 스냅샷을 저장하고 이전 행은 그대로다")
    void resolvedTargetCanBeReportedAgain() {
        ReportReceipt first = service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, "ETC", "처음"));
        reports.resolveFirst();
        targets.found = new ReportTargetLookup(ReportTargetType.FEED, 41L, 9L, "수정된 본문");

        ReportReceipt again = service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, "ETC", "다시"));

        assertThat(again.id()).isNotEqualTo(first.id());
        assertThat(reports.saved.getFirst().getSnapshot()).isEqualTo("FULL:9:" + ORIGINAL);
        assertThat(reports.saved.getFirst().getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(reports.saved.get(1).getSnapshot()).isEqualTo("FULL:9:수정된 본문");
        assertThat(reports.saved.get(1).getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("회원 번호, 대상, 사유, 상세 내용이 잘못되면 조회하지 않는다")
    void invalidRequestDoesNotLookUp() {
        assertCode(() -> service.report(new ReportCommand(0L, ReportTargetType.FEED, 41L, "SPAM", null)),
                CommonErrorCode.UNAUTHORIZED);
        assertCode(() -> service.report(new ReportCommand(2L, ReportTargetType.USER, 41L, "SPAM", null)),
                ModerationErrorCode.INVALID_REPORT_TARGET);
        assertCode(() -> service.report(new ReportCommand(2L, ReportTargetType.COLLECTION, 41L, "SPAM", null)),
                ModerationErrorCode.INVALID_REPORT_TARGET);
        assertCode(() -> service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, "OTHER", null)),
                ModerationErrorCode.INVALID_REPORT_REASON);
        assertCode(() -> service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, "ETC", "   ")),
                ModerationErrorCode.REPORT_DETAIL_REQUIRED);
        assertCode(() -> service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, "SPAM", "가".repeat(501))),
                ModerationErrorCode.REPORT_DETAIL_TOO_LONG);

        assertThat(targets.calls).isZero();
        assertThat(reports.saved).isEmpty();
    }

    @Test
    @DisplayName("없거나 볼 수 없는 대상은 본문 없이 404이고 저장하지 않는다")
    void missingTargetDoesNotSave() {
        targets.found = null;

        BusinessException error = assertCode(
                () -> service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, "SPAM", null)),
                ModerationErrorCode.REPORT_TARGET_NOT_FOUND);

        assertThat(error.getMessage()).doesNotContain(ORIGINAL);
        assertThat(reports.saved).isEmpty();
        assertThat(writer.last).isNull();
    }

    @Test
    @DisplayName("자기 글은 작성자 번호가 같아 400이고 본문을 응답하지 않는다")
    void selfReportIsRejected() {
        targets.found = new ReportTargetLookup(ReportTargetType.COMMENT, 8L, 2L, ORIGINAL);

        BusinessException error = assertCode(
                () -> service.report(new ReportCommand(2L, ReportTargetType.COMMENT, 8L, "ABUSE", null)),
                ModerationErrorCode.SELF_REPORT);

        assertThat(error.getMessage()).doesNotContain(ORIGINAL).doesNotContain("2");
        assertThat(reports.saved).isEmpty();
    }

    @Test
    @DisplayName("같은 대기 신고는 사유가 달라도 저장하지 않는다")
    void duplicatePendingDoesNotSaveAgain() {
        service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, "SPAM", null));

        assertCode(() -> service.report(new ReportCommand(2L, ReportTargetType.FEED, 41L, "ABUSE", "다른 사유")),
                ModerationErrorCode.DUPLICATE_REPORT);

        assertThat(reports.saved).hasSize(1);
        assertThat(reports.saved.getFirst().getReason()).isEqualTo("SPAM");
    }

    private static BusinessException assertCode(Runnable call, ErrorCode code) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException exception = (BusinessException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(code);
        return exception;
    }

    private static final class FakeTargets implements ReportTargetLookupPort {
        private ReportTargetLookup found;
        private int calls;

        @Override
        public Optional<ReportTargetLookup> findReportable(long reporterId, ReportTargetType targetType, long targetId) {
            calls++;
            return Optional.ofNullable(found);
        }
    }

    private static final class FakeWriter implements ReportSnapshotWriter {
        private ReportSnapshot last;

        @Override
        public String write(ReportSnapshot snapshot) {
            last = snapshot;
            return snapshot.textScope() + ":" + snapshot.reportedUserId() + ":" + snapshot.text();
        }
    }

    private static final class FakeReports implements ReportPersistencePort {
        private final List<Report> saved = new ArrayList<>();

        @Override
        public Optional<Report> findById(long id) {
            return saved.stream().filter(report -> report.getId() == id).findFirst();
        }

        @Override
        public boolean existsPending(long reporterId, ReportTargetType targetType, long targetId) {
            return saved.stream().anyMatch(report -> report.getStatus() == ReportStatus.PENDING
                    && report.getReporterId() == reporterId
                    && report.getTargetType() == targetType
                    && report.getTargetId() == targetId);
        }

        @Override
        public Report save(Report report) {
            Report stored = report.toBuilder().id((long) saved.size() + 1).build();
            saved.add(stored);
            return stored;
        }

        private void resolveFirst() {
            Report first = saved.getFirst();
            saved.set(0, first.toBuilder().status(ReportStatus.RESOLVED).build());
        }

        @Override
        public List<Report> findByStatus(ReportStatus status) {
            return List.of();
        }

        @Override
        public ReportDismissal dismissIfPrimaryPending(long reportId, long adminId, String memo, OffsetDateTime now) {
            throw new UnsupportedOperationException();
        }
    }
}
