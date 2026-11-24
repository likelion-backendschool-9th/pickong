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
