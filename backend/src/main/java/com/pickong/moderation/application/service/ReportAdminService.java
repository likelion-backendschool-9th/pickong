package com.pickong.moderation.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.application.dto.ReportDismissal;
import com.pickong.moderation.application.dto.ReportPartyView;
import com.pickong.moderation.application.dto.ReportTargetKey;
import com.pickong.moderation.application.dto.ReportTargetPreview;
import com.pickong.moderation.application.dto.ReportView;
import com.pickong.moderation.application.port.in.ReportAdminUseCase;
import com.pickong.moderation.application.port.out.ReportPartyLookupPort;
import com.pickong.moderation.application.port.out.ReportPersistencePort;
import com.pickong.moderation.application.port.out.ReportTargetPreviewPort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.ReportRules;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportAction;
import com.pickong.moderation.domain.vo.ReportStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 신고 조회와 기각.
 * <p>
 * 숨김과 제재는 대상 모듈의 공개 기능이 없어 여기서 실행하지 않습니다.
 * 그 요청은 검증 뒤에 거절하고, 신고 행은 그대로 둡니다.
 */
@Service
@Transactional(readOnly = true)
public class ReportAdminService implements ReportAdminUseCase {

    private final ReportPersistencePort reports;
    private final ReportTargetPreviewPort previews;
    private final ReportPartyLookupPort parties;
    private final Clock clock;

    public ReportAdminService(ReportPersistencePort reports, ReportTargetPreviewPort previews,
            ReportPartyLookupPort parties, Clock clock) {
        this.reports = reports;
        this.previews = previews;
        this.parties = parties;
        this.clock = clock;
    }

    @Override
    public List<ReportView> list(String status) {
        return toViews(reports.findByStatus(ReportRules.parseStatus(status)));
    }

    @Override
    @Transactional
    public ReportView resolve(long reportId, long adminId, String action, String memo, String sanctionType) {
        ReportAction parsed = ReportRules.parseAction(action);
        ReportRules.checkSanctionType(parsed, sanctionType);
        String normalizedMemo = ReportRules.normalizeMemo(parsed, memo);
        if (parsed != ReportAction.DISMISS) {
            rejectUnsupported(reportId);
        }
        ReportDismissal dismissal = reports.dismissIfPrimaryPending(
                reportId, adminId, normalizedMemo, OffsetDateTime.now(clock));
        Report dismissed = switch (dismissal.kind()) {
            case MISSING -> throw new BusinessException(ModerationErrorCode.REPORT_NOT_FOUND);
            case ALREADY_RESOLVED -> throw new BusinessException(ModerationErrorCode.ALREADY_RESOLVED);
            case DISMISSED -> dismissal.report();
        };
        return toViews(List.of(dismissed)).getFirst();
    }

    /** 기능이 없는 액션은 완료로 저장하지 않습니다. 이미 처리된 신고는 그 사실을 먼저 알립니다. */
    private void rejectUnsupported(long reportId) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new BusinessException(ModerationErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(ModerationErrorCode.ALREADY_RESOLVED);
        }
        throw new BusinessException(ModerationErrorCode.REPORT_ACTION_NOT_SUPPORTED);
    }

    private List<ReportView> toViews(List<Report> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<ReportTargetKey, ReportTargetPreview> targets = previews.find(rows);
        Map<Long, ReportPartyView> people = parties.findByUserIds(userIds(rows, targets));
        return rows.stream().map(row -> {
            ReportTargetKey key = new ReportTargetKey(row.getTargetType(), row.getTargetId());
            ReportTargetPreview target = targets.get(key);
            if (target == null) {
                target = new ReportTargetPreview("내용을 확인할 수 없어요", null, null, null);
            }
            return new ReportView(row, target, person(people, target.reportedUserId()), person(people, row.getReporterId()));
        }).toList();
    }

    private static ReportPartyView person(Map<Long, ReportPartyView> people, Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return people.get(userId);
    }

    private static Set<Long> userIds(List<Report> rows, Map<ReportTargetKey, ReportTargetPreview> targets) {
        Set<Long> ids = new HashSet<>();
        for (Report row : rows) {
            if (row.getReporterId() != null && row.getReporterId() > 0) {
                ids.add(row.getReporterId());
            }
            ReportTargetPreview target = targets.get(new ReportTargetKey(row.getTargetType(), row.getTargetId()));
            if (target != null && target.reportedUserId() != null && target.reportedUserId() > 0) {
                ids.add(target.reportedUserId());
            }
        }
        return ids;
    }
}
