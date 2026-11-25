package com.pickong.moderation.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.admin.application.dto.AdminActionLogEntry;
import com.pickong.admin.application.port.in.AdminActionLogUseCase;
import com.pickong.feed.application.port.in.FeedModerationUseCase;
import com.pickong.moderation.application.dto.SanctionCommand;
import com.pickong.moderation.application.port.in.SanctionUseCase;
import com.pickong.moderation.application.dto.ReportDismissal;
import com.pickong.moderation.application.dto.ReportPartyView;
import com.pickong.moderation.application.dto.ReportTargetPreview;
import com.pickong.moderation.application.dto.ReportTargetVisibility;
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
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선택 신고 잠금 → 공통 제재/콘텐츠 포트 → 선택 신고 완료. 모든 업무 변경은 같은 트랜잭션입니다.
 */
@Service
@Transactional(readOnly = true)
public class ReportAdminService implements ReportAdminUseCase {

    private final ReportPersistencePort reports;
    private final ReportTargetPreviewPort previews;
    private final ReportPartyLookupPort parties;
    private final Clock clock;
    private final SanctionUseCase sanctions;
    private final FeedModerationUseCase contents;
    private final AdminActionLogUseCase logs;

    public ReportAdminService(ReportPersistencePort reports, ReportTargetPreviewPort previews,
            ReportPartyLookupPort parties, SanctionUseCase sanctions, FeedModerationUseCase contents,
            AdminActionLogUseCase logs, Clock clock) {
        this.reports = reports;
        this.previews = previews;
        this.parties = parties;
        this.clock = clock;
        this.sanctions = sanctions;
        this.contents = contents;
        this.logs = logs;
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
        if (parsed != ReportAction.DISMISS) return resolveAction(reportId, adminId, parsed, normalizedMemo, sanctionType);
        ReportDismissal dismissal = reports.dismissIfPrimaryPending(
                reportId, adminId, normalizedMemo, OffsetDateTime.now(clock));
        Report dismissed = switch (dismissal.kind()) {
            case MISSING -> throw new BusinessException(ModerationErrorCode.REPORT_NOT_FOUND);
            case ALREADY_RESOLVED -> throw new BusinessException(ModerationErrorCode.ALREADY_RESOLVED);
            case DISMISSED -> dismissal.report();
        };
        logs.record(new AdminActionLogEntry(adminId, "DISMISS_REPORT", "REPORT", reportId,
                normalizedMemo, null, reportId, dismissed.getHandledAt()));
        return toViews(List.of(dismissed)).getFirst();
    }

    private ReportView resolveAction(long reportId, long adminId, ReportAction action, String memo, String sanctionType) {
        Report report = reports.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ModerationErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(ModerationErrorCode.ALREADY_RESOLVED);
        }
        if (report.getTargetType() == ReportTargetType.COLLECTION) {
            throw new BusinessException(ModerationErrorCode.REPORT_ACTION_NOT_SUPPORTED);
        }
        if (action == ReportAction.SANCTION_USER) {
            ReportTargetPreview target = previews.find(List.of(report)).get(reportId);
            Long userId = report.getTargetType() == ReportTargetType.USER ? report.getTargetId()
                    : target == null ? null : target.reportedUserId();
            if (userId == null || userId <= 0) throw new BusinessException(ModerationErrorCode.REPORT_TARGET_NOT_FOUND);
            sanctions.sanction(SanctionCommand.fromAdminType(userId, sanctionType.strip(), memo, adminId, reportId));
        } else {
            switch (report.getTargetType()) {
                case FEED -> contents.setHidden(report.getTargetId(), true, memo, adminId, reportId);
                case COMMENT -> contents.setCommentHidden(report.getTargetId(), true, memo, adminId, reportId);
                default -> throw new BusinessException(ModerationErrorCode.REPORT_TARGET_ACTION_NOT_ALLOWED);
            }
        }
        Report resolved = report.resolve(action, adminId, memo, OffsetDateTime.now(clock));
        if (!reports.resolveIfPending(resolved)) throw new BusinessException(ModerationErrorCode.ALREADY_RESOLVED);
        // 공통 제재/숨김이 이미 로그를 남기므로 여기서 동일 업무 로그를 중복 생성하지 않습니다.
        return toViews(List.of(resolved)).getFirst();
    }

    private List<ReportView> toViews(List<Report> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, ReportTargetPreview> targets = previews.find(rows);
        Map<Long, ReportPartyView> people = parties.findByUserIds(userIds(rows, targets));
        return rows.stream().map(row -> {
            ReportTargetPreview target = row.getId() == null ? null : targets.get(row.getId());
            if (target == null) {
                target = new ReportTargetPreview(
                        "내용을 확인할 수 없어요", null, null, null, null, null, null, ReportTargetVisibility.UNAVAILABLE);
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

    private static Set<Long> userIds(List<Report> rows, Map<Long, ReportTargetPreview> targets) {
        Set<Long> ids = new HashSet<>();
        for (Report row : rows) {
            if (row.getReporterId() != null && row.getReporterId() > 0) {
                ids.add(row.getReporterId());
            }
            ReportTargetPreview target = row.getId() == null ? null : targets.get(row.getId());
            if (target != null && target.reportedUserId() != null && target.reportedUserId() > 0) {
                ids.add(target.reportedUserId());
            }
        }
        return ids;
    }
}
