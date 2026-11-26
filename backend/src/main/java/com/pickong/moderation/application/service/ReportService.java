package com.pickong.moderation.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.moderation.application.ReportSnapshots;
import com.pickong.moderation.application.dto.ReportCommand;
import com.pickong.moderation.application.dto.ReportReceipt;
import com.pickong.moderation.application.dto.ReportTargetLookup;
import com.pickong.moderation.application.port.in.ReportUseCase;
import com.pickong.moderation.application.port.out.ReportPersistencePort;
import com.pickong.moderation.application.port.out.ReportSnapshotWriter;
import com.pickong.moderation.application.port.out.ReportTargetLookupPort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.ReportRules;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportReason;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 신고 접수. 관리자 목록과 기각은 {@link ReportAdminService}가 담당합니다. */
@Service
@Transactional(readOnly = true)
public class ReportService implements ReportUseCase {

    private final ReportTargetLookupPort targets;
    private final ReportPersistencePort reports;
    private final ReportSnapshotWriter snapshots;
    private final Clock clock;

    public ReportService(ReportTargetLookupPort targets, ReportPersistencePort reports,
            ReportSnapshotWriter snapshots, Clock clock) {
        this.targets = targets;
        this.reports = reports;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReportReceipt report(ReportCommand command) {
        if (command.reporterId() <= 0) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        ReportTargetType targetType = ReportRules.reportableTarget(command.targetType(), command.targetId());
        ReportReason reason = ReportReason.parse(command.reason());
        String detail = ReportRules.normalizeDetail(reason, command.detail());
        ReportTargetLookup target = targets.findReportable(command.reporterId(), targetType, command.targetId())
                .orElseThrow(() -> new BusinessException(ModerationErrorCode.REPORT_TARGET_NOT_FOUND));
        if (target.authorId() == command.reporterId()) {
            throw new BusinessException(ModerationErrorCode.SELF_REPORT);
        }
        if (reports.existsPending(command.reporterId(), targetType, command.targetId())) {
            throw new BusinessException(ModerationErrorCode.DUPLICATE_REPORT);
        }
        String snapshot = snapshots.write(ReportSnapshots.from(target));
        Report saved = reports.save(Report.pending(
                command.reporterId(),
                target.targetType(),
                target.targetId(),
                reason.name(),
                detail,
                snapshot,
                OffsetDateTime.now(clock)));
        return ReportReceipt.from(saved);
    }
}
