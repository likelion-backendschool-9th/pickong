package com.pickong.moderation.domain.model;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.ReportRules;
import com.pickong.moderation.domain.vo.ReportAction;
import com.pickong.moderation.domain.vo.ReportStatus;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 신고 애그리거트. targetId는 여러 테이블을 가리키므로 외래 키로 묶지 않습니다.
 * <p>snapshot은 신고 시점의 대상 스냅샷입니다. 값은 직접 바꾸지 않고 기각 결과로 새 객체를 돌려줍니다.
 * Spring · JPA · HTTP에 의존하지 않습니다.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Report {

    private Long id;
    private Long reporterId;
    private ReportTargetType targetType;
    private Long targetId;
    private String reason;
    private String detail;
    private ReportStatus status;
    private ReportAction resolutionAction;
    private String resolutionMemo;
    private Long handledBy;
    private OffsetDateTime handledAt;
    private String snapshot;
    private OffsetDateTime createdAt;

    /**
     * 새 신고를 대기 상태로 만듭니다. 처리 정보는 비워 두고, 스냅샷 문자열은 그대로 둡니다.
     * 사유는 기존 컬럼처럼 50자 이내의 비어 있지 않은 문자열입니다.
     */
    public static Report pending(
            long reporterId,
            ReportTargetType targetType,
            long targetId,
            String reason,
            String detail,
            String snapshot,
            OffsetDateTime createdAt) {
        if (reporterId <= 0 || targetId <= 0) {
            throw new IllegalArgumentException("신고자와 대상 id가 필요합니다.");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("신고 대상 종류가 필요합니다.");
        }
        if (reason == null || reason.isBlank() || reason.length() > 50) {
            throw new IllegalArgumentException("신고 사유는 50자 이내여야 합니다.");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("신고 당시 내용이 필요합니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("신고 시각이 필요합니다.");
        }
        return Report.builder()
                .reporterId(reporterId)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .detail(detail)
                .status(ReportStatus.PENDING)
                .snapshot(snapshot)
                .createdAt(createdAt)
                .build();
    }

    /**
     * 대기 신고를 기각합니다. 메모는 없어도 되고, 있으면 200자까지입니다.
     * 이미 처리된 신고는 상태와 기존 처리 정보를 바꾸지 않습니다.
     */
    public Report dismiss(long adminId, String memo, OffsetDateTime now) {
        if (status != ReportStatus.PENDING) {
            throw new BusinessException(ModerationErrorCode.ALREADY_RESOLVED);
        }
        if (adminId <= 0) {
            throw new IllegalArgumentException("처리 관리자 id가 필요합니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("처리 시각이 필요합니다.");
        }
        return toBuilder()
                .status(ReportStatus.RESOLVED)
                .resolutionAction(ReportAction.DISMISS)
                .resolutionMemo(ReportRules.normalizeMemo(ReportAction.DISMISS, memo))
                .handledBy(adminId)
                .handledAt(now)
                .build();
    }
}
