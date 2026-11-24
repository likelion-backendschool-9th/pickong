package com.pickong.moderation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.application.dto.ReportDismissal;
import com.pickong.moderation.application.dto.ReportTargetPreview;
import com.pickong.moderation.application.dto.ReportTargetVisibility;
import com.pickong.moderation.application.dto.ReportView;
import com.pickong.moderation.application.port.out.ReportPersistencePort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportStatus;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신고 처리 서비스. 저장소는 가짜라 숨김 · 제재가 완료로 저장되지 않는지만 확인합니다. */
class ReportAdminServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-24T05:00:00Z");

    private final FakeReports reports = new FakeReports();
    private final ReportAdminService service = new ReportAdminService(
            reports, request -> Map.of(), request -> Map.of(),
            Clock.fixed(Instant.parse("2026-11-24T05:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void pendingReport() {
        reports.current = pending();
    }

    @Test
    @DisplayName("기각만 저장소에 넘긴다")
    void dismissCallsStore() {
        service.resolve(4L, 7L, "DISMISS", " 문제 없음 ", null);

        assertThat(reports.dismissCalls).isEqualTo(1);
        assertThat(reports.lastMemo).isEqualTo("문제 없음");
        assertThat(reports.lastAdminId).isEqualTo(7L);
    }

    @Test
    @DisplayName("숨김과 제재는 저장하지 않고 501이며 신고는 대기로 남는다")
    void unsupportedActionsDoNotResolve() {
        assertCode(() -> service.resolve(4L, 7L, "HIDE_CONTENT", "스팸", null),
                ModerationErrorCode.REPORT_ACTION_NOT_SUPPORTED);
        assertCode(() -> service.resolve(4L, 7L, "SANCTION_USER", "반복", "BAN"),
                ModerationErrorCode.REPORT_ACTION_NOT_SUPPORTED);

        assertThat(reports.dismissCalls).isZero();
        assertThat(reports.current.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("이미 처리된 신고의 숨김 요청도 완료로 덮지 않고 409이다")
    void unsupportedOnResolvedIsConflict() {
        reports.current = pending().dismiss(3L, "이전", NOW.minusHours(1));

        assertCode(() -> service.resolve(4L, 7L, "HIDE_CONTENT", "스팸", null), ModerationErrorCode.ALREADY_RESOLVED);
        assertThat(reports.dismissCalls).isZero();
        assertThat(reports.current.getHandledBy()).isEqualTo(3L);
    }

    @Test
    @DisplayName("사유와 제재 종류가 잘못되면 저장소에 기각을 호출하지 않는다")
    void invalidRequestDoesNotDismiss() {
        assertCode(() -> service.resolve(4L, 7L, "HIDE_CONTENT", "  ", null), ModerationErrorCode.RESOLUTION_MEMO_REQUIRED);
        assertCode(() -> service.resolve(4L, 7L, "DISMISS", "가".repeat(201), null), ModerationErrorCode.RESOLUTION_MEMO_TOO_LONG);
        assertCode(() -> service.resolve(4L, 7L, "SANCTION_USER", "사유", "WEEK"), ModerationErrorCode.INVALID_SANCTION_TYPE);
        assertCode(() -> service.resolve(4L, 7L, "DISMISS", null, "BAN"), ModerationErrorCode.SANCTION_TYPE_NOT_ALLOWED);
        assertCode(() -> service.resolve(4L, 7L, "NOPE", null, null), ModerationErrorCode.INVALID_REPORT_ACTION);
        assertThat(reports.dismissCalls).isZero();
    }

    @Test
    @DisplayName("같은 대상의 신고도 각자의 미리보기를 유지한다")
    void listKeepsPreviewPerReport() {
        Report newer = report(11L, "수정된 글", NOW);
        Report older = report(10L, "욕설", NOW.minusHours(2));
        reports.rows = List.of(newer, older);
        ReportAdminService perReport = new ReportAdminService(
                reports,
                request -> Map.of(
                        11L, new ReportTargetPreview("수정된 글", null, null, 8L, null, null, null, ReportTargetVisibility.LIVE),
                        10L, new ReportTargetPreview(
                                "욕설", "https://example.com/old.jpg", null, 8L, null, null, null, ReportTargetVisibility.LIVE)),
                request -> Map.of(),
                Clock.fixed(Instant.parse("2026-11-24T05:00:00Z"), ZoneOffset.UTC));

        List<ReportView> views = perReport.list("PENDING");

        assertThat(views)
                .extracting(view -> view.report().getId(), view -> view.target().text(), view -> view.target().imageUrl())
                .containsExactly(
                        Tuple.tuple(11L, "수정된 글", null),
                        Tuple.tuple(10L, "욕설", "https://example.com/old.jpg"));
    }

    @Test
    @DisplayName("없는 신고는 404이고 빈 목록은 빈 배열이다")
    void missingAndEmpty() {
        reports.current = null;
        assertCode(() -> service.resolve(9L, 7L, "DISMISS", null, null), ModerationErrorCode.REPORT_NOT_FOUND);
        reports.rows = List.of();
        assertThat(service.list("PENDING")).isEmpty();
        assertCode(() -> service.list("OPEN"), ModerationErrorCode.INVALID_REPORT_STATUS);
    }

    private static void assertCode(Runnable call, ModerationErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(code);
    }

    private static Report pending() {
        return report(4L, null, NOW);
    }

    private static Report report(long id, String snapshot, OffsetDateTime createdAt) {
        return Report.builder()
                .id(id)
                .reporterId(2L)
                .targetType(ReportTargetType.FEED)
                .targetId(9L)
                .reason("SPAM")
                .status(ReportStatus.PENDING)
                .snapshot(snapshot)
                .createdAt(createdAt)
                .build();
    }

    private static final class FakeReports implements ReportPersistencePort {
        private Report current;
        private List<Report> rows = new ArrayList<>();
        private int dismissCalls;
        private String lastMemo;
        private long lastAdminId;

        @Override
        public Optional<Report> findById(long id) {
            if (current == null || !current.getId().equals(id)) {
                return Optional.empty();
            }
            return Optional.of(current);
        }

        @Override
        public List<Report> findByStatus(ReportStatus status) {
            return rows.stream().filter(report -> report.getStatus() == status).toList();
        }

        @Override
        public ReportDismissal dismissIfPrimaryPending(long reportId, long adminId, String memo, OffsetDateTime now) {
            if (current == null || !current.getId().equals(reportId)) {
                return ReportDismissal.missing();
            }
            dismissCalls++;
            lastMemo = memo;
            lastAdminId = adminId;
            Report dismissed = current.dismiss(adminId, memo, now);
            current = dismissed;
            return ReportDismissal.dismissed(dismissed);
        }
    }
}
