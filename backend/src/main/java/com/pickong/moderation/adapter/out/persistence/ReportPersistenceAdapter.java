package com.pickong.moderation.adapter.out.persistence;

import com.pickong.moderation.adapter.out.persistence.entity.ReportJpaEntity;
import com.pickong.moderation.adapter.out.persistence.repository.ReportJpaRepository;
import com.pickong.moderation.application.dto.ReportDismissal;
import com.pickong.moderation.application.port.out.ReportPersistencePort;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportAction;
import com.pickong.moderation.domain.vo.ReportStatus;
import com.pickong.moderation.domain.vo.ReportTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 신고 저장소.
 * <p>
 * 기각은 선택한 신고 행만 잠그고 그 행만 바꿉니다. 같은 대상의 다른 신고는 건드리지 않습니다.
 * 벌크 UPDATE는 영속성 컨텍스트에 이전 상태를 남겨 같은 트랜잭션의 재조회가 옛 값을 보게 하므로 쓰지 않습니다.
 * 선택한 신고가 이미 완료면 잠금만 하고 행을 바꾸지 않습니다.
 */
@Component
public class ReportPersistenceAdapter implements ReportPersistencePort {

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
    public List<Report> findByStatus(ReportStatus status) {
        return repository.findByStatusOrderByCreatedAtDescIdDesc(status.name()).stream()
                .map(ReportPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public ReportDismissal dismissIfPrimaryPending(long reportId, long adminId, String memo, OffsetDateTime now) {
        List<Long> lockedIds = jdbc.query("""
                SELECT id
                  FROM reports
                 WHERE id = ?
                   FOR UPDATE
                """, (rs, rowNum) -> rs.getLong("id"), reportId);
        if (lockedIds.isEmpty()) {
            return ReportDismissal.missing();
        }
        ReportJpaEntity primary = entityManager.find(ReportJpaEntity.class, reportId);
        if (primary == null) {
            return ReportDismissal.missing();
        }
        entityManager.refresh(primary);
        if (!"PENDING".equals(primary.getStatus())) {
            return ReportDismissal.alreadyResolved();
        }
        Report dismissed = toDomain(primary).dismiss(adminId, memo, now);
        primary.applyDismissal(dismissed.getResolutionAction().name(), dismissed.getResolutionMemo(), adminId, now);
        entityManager.flush();
        return ReportDismissal.dismissed(dismissed);
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
