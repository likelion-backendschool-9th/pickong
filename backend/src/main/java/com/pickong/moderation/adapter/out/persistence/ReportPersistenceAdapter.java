package com.pickong.moderation.adapter.out.persistence;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.adapter.out.persistence.entity.ReportJpaEntity;
import com.pickong.moderation.adapter.out.persistence.repository.ReportJpaRepository;
import com.pickong.moderation.application.dto.ReportDismissal;
import com.pickong.moderation.application.port.out.ReportPersistencePort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportAction;
import com.pickong.moderation.domain.vo.ReportStatus;
import com.pickong.moderation.domain.vo.ReportTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 저장소.
 * <p>
 * 선택한 신고 행만 잠그고 그 행의 처리 필드만 바꿉니다. 같은 대상의 다른 신고는 건드리지 않습니다.
 * 조건부 UPDATE 후 해당 엔티티를 refresh해 같은 트랜잭션에서도 처리 결과를 읽습니다.
 * 선택한 신고가 이미 완료면 잠금만 하고 행을 바꾸지 않습니다.
 */
@Component
public class ReportPersistenceAdapter implements ReportPersistencePort {

    static final String PENDING_REPORTER_TARGET = "ux_reports_pending_reporter_target";

    private final ReportJpaRepository repository;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    public ReportPersistenceAdapter(ReportJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Report> findById(long id) {
        return repository.findById(id).map(ReportPersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsPending(long reporterId, ReportTargetType targetType, long targetId) {
        if (reporterId <= 0 || targetId <= 0 || targetType == null) {
            return false;
        }
        return repository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                reporterId, targetType.name(), targetId, ReportStatus.PENDING.name());
    }

    @Override
    public Report save(Report report) {
        if (report.getId() != null || report.getStatus() != ReportStatus.PENDING) {
            throw new IllegalArgumentException("새 대기 신고만 저장합니다.");
        }
        try {
            return toDomain(repository.saveAndFlush(ReportJpaEntity.forInsert(report)));
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
    }

    @Override
    public List<Report> findByStatus(ReportStatus status) {
        return repository.findByStatusOrderByCreatedAtDescIdDesc(status.name()).stream()
                .map(ReportPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ReportDismissal dismissIfPrimaryPending(long reportId, long adminId, String memo, OffsetDateTime now) {
        Optional<Report> found = findByIdForUpdate(reportId);
        if (found.isEmpty()) return ReportDismissal.missing();
        if (found.get().getStatus() != ReportStatus.PENDING) return ReportDismissal.alreadyResolved();
        Report dismissed = found.get().dismiss(adminId, memo, now);
        return resolveIfPending(dismissed) ? ReportDismissal.dismissed(dismissed) : ReportDismissal.alreadyResolved();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Report> findByIdForUpdate(long reportId) {
        List<Long> lockedIds = jdbc.query("""
                SELECT id
                  FROM reports
                 WHERE id = ?
                   FOR UPDATE
                """, (rs, rowNum) -> rs.getLong("id"), reportId);
        if (lockedIds.isEmpty()) {
            return Optional.empty();
        }
        ReportJpaEntity primary = entityManager.find(ReportJpaEntity.class, reportId);
        if (primary == null) {
            return Optional.empty();
        }
        entityManager.refresh(primary);
        return Optional.of(toDomain(primary));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean resolveIfPending(Report resolved) {
        if (resolved.getId() == null || resolved.getStatus() != ReportStatus.RESOLVED || resolved.getResolutionAction() == null) {
            throw new IllegalArgumentException("검증된 처리 결과가 필요합니다.");
        }
        // 다른 모듈의 상태 변경이 영속성 컨텍스트를 비워도 선택한 행의 처리 정보만 저장합니다.
        int changed = jdbc.update("""
                UPDATE reports SET status='RESOLVED', resolution_action=?, resolution_memo=?, handled_by=?, handled_at=?
                 WHERE id=? AND status='PENDING'
                """, resolved.getResolutionAction().name(), resolved.getResolutionMemo(), resolved.getHandledBy(),
                resolved.getHandledAt(), resolved.getId());
        if (changed == 1) {
            ReportJpaEntity current = entityManager.find(ReportJpaEntity.class, resolved.getId());
            if (current != null) entityManager.refresh(current);
        }
        return changed == 1;
    }

    /** 대기 신고 중복 인덱스만 중복 신고로 바꿉니다. FK · CHECK 위반은 그대로 둡니다. */
    private static RuntimeException translate(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause instanceof SQLException sql
                && "23505".equals(sql.getSQLState())
                && String.valueOf(cause.getMessage()).contains(PENDING_REPORTER_TARGET)) {
            return new BusinessException(ModerationErrorCode.DUPLICATE_REPORT);
        }
        return exception;
    }

    private static Report toDomain(ReportJpaEntity entity) {
        return Report.builder()
                .id(entity.getId())
                .reporterId(entity.getReporterId())
                .targetType(ReportTargetType.valueOf(entity.getTargetType()))
                .targetId(entity.getTargetId())
                .reason(entity.getReason())
                .detail(entity.getDetail())
                .status(ReportStatus.valueOf(entity.getStatus()))
                .resolutionAction(entity.getResolutionAction() == null
                        ? null
                        : ReportAction.valueOf(entity.getResolutionAction()))
                .resolutionMemo(entity.getResolutionMemo())
                .handledBy(entity.getHandledBy())
                .handledAt(entity.getHandledAt())
                .snapshot(entity.getSnapshot())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
