package com.pickong.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportAction;
import com.pickong.moderation.domain.vo.ReportStatus;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.OffsetDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신고 기각 규칙. Spring · DB 없이 실행합니다. */
class ReportTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-24T05:00:00Z");

    @Test
    @DisplayName("기각하면 완료 상태와 처리 액션, 관리자, 시각이 같이 남고 원래 객체는 대기입니다")
    void dismissResolvesWithoutChangingTheOriginal() {
        Report pending = pending();

        Report dismissed = pending.dismiss(7L, "  문제 없음  ", NOW);

        assertThat(pending.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(pending.getResolutionAction()).isNull();
        assertThat(dismissed.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(dismissed.getResolutionAction()).isEqualTo(ReportAction.DISMISS);
        assertThat(dismissed.getResolutionMemo()).isEqualTo("문제 없음");
        assertThat(dismissed.getHandledBy()).isEqualTo(7L);
        assertThat(dismissed.getHandledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("기각 메모는 비어도 되고, 200자를 넘기거나 이미 처리된 신고는 거절합니다")
    void dismissMemoAndReplay() {
        Report dismissed = pending().dismiss(7L, "   ", NOW);
        assertThat(dismissed.getResolutionMemo()).isNull();

        assertError(() -> pending().dismiss(7L, "가".repeat(201), NOW), ModerationErrorCode.RESOLUTION_MEMO_TOO_LONG);
        assertError(() -> dismissed.dismiss(8L, "다시", NOW.plusHours(1)), ModerationErrorCode.ALREADY_RESOLVED);
        assertThat(dismissed.getHandledBy()).isEqualTo(7L);
    }

    @Test
    @DisplayName("숨김과 제재는 메모가 필수이고, 다른 액션의 제재 종류는 받지 않습니다")
    void requestRules() {
        assertThat(ReportRules.normalizeMemo(ReportAction.DISMISS, "  ")).isNull();
        assertError(
                () -> ReportRules.normalizeMemo(ReportAction.HIDE_CONTENT, "  "),
                ModerationErrorCode.RESOLUTION_MEMO_REQUIRED);
        assertError(
                () -> ReportRules.normalizeMemo(ReportAction.SANCTION_USER, null),
                ModerationErrorCode.RESOLUTION_MEMO_REQUIRED);
        assertThat(ReportRules.normalizeMemo(ReportAction.HIDE_CONTENT, " 스팸 ")).isEqualTo("스팸");

        ReportRules.checkSanctionType(ReportAction.SANCTION_USER, " SUSPEND_7 ");
        assertError(
                () -> ReportRules.checkSanctionType(ReportAction.SANCTION_USER, "WEEK"),
                ModerationErrorCode.INVALID_SANCTION_TYPE);
        assertError(
                () -> ReportRules.checkSanctionType(ReportAction.DISMISS, "BAN"),
                ModerationErrorCode.SANCTION_TYPE_NOT_ALLOWED);
        assertError(() -> ReportRules.parseAction("HIDE"), ModerationErrorCode.INVALID_REPORT_ACTION);
        assertError(() -> ReportRules.parseStatus("HANDLED"), ModerationErrorCode.INVALID_REPORT_STATUS);
    }

    private static Report pending() {
        return Report.builder()
                .id(4L)
                .reporterId(2L)
                .targetType(ReportTargetType.FEED)
                .targetId(9L)
                .reason("SPAM")
                .status(ReportStatus.PENDING)
                .createdAt(NOW)
                .build();
    }

    private static void assertError(ThrowingCallable call, ModerationErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(code);
    }
}
