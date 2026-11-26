package com.pickong.moderation.adapter.out.persistence;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.adapter.out.persistence.entity.UserSanctionJpaEntity;
import com.pickong.moderation.adapter.out.persistence.repository.UserSanctionJpaRepository;
import com.pickong.moderation.application.port.out.SanctionPersistencePort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.model.Sanction;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SanctionPersistenceAdapter implements SanctionPersistencePort {
    private final UserSanctionJpaRepository repository;

    public SanctionPersistenceAdapter(UserSanctionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Sanction save(Sanction sanction) {
        try {
            return repository.saveAndFlush(UserSanctionJpaEntity.forInsert(sanction)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
    }

    @Override
    public Optional<Sanction> findById(long id) {
        return repository.findById(id).map(UserSanctionJpaEntity::toDomain);
    }

    @Override
    public Optional<Sanction> findBySourceReportId(long sourceReportId) {
        return repository.findBySourceReportId(sourceReportId).map(UserSanctionJpaEntity::toDomain);
    }

    @Override
    public List<Sanction> findByUserId(long userId) {
        return repository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(UserSanctionJpaEntity::toDomain).toList();
    }

    @Override
    public List<Sanction> findOpenByUserId(long userId) {
        return repository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAscIdAsc(userId).stream()
                .map(UserSanctionJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean endIfOpen(Sanction ended) {
        if (ended.getId() == null || ended.getUserId() == null || ended.getEndedAt() == null || ended.getEndReason() == null) {
            throw new IllegalArgumentException("저장된 제재의 종료 결과가 필요합니다.");
        }
        return repository.endIfOpen(ended.getId(), ended.getUserId(), ended.getEndedAt(), ended.getEndReason().name(),
                ended.getRevokedAt(), ended.getRevokedByAdminId(), ended.getRevokeReason()) == 1;
    }

    /** V15의 두 중복 제약만 변환합니다. FK·CHECK 등 다른 무결성 오류는 원래 예외를 유지합니다. */
    private static RuntimeException translate(DataIntegrityViolationException exception) {
        if (exception.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            String message = String.valueOf(sql.getMessage());
            if (message.contains("\"uk_user_sanctions_source_report\"")) {
                return new BusinessException(ModerationErrorCode.DUPLICATE_SANCTION_SOURCE);
            }
            if (message.contains("\"ux_user_sanctions_open_restriction\"")) {
                return new BusinessException(ModerationErrorCode.ACTIVE_SANCTION_EXISTS);
            }
        }
        return exception;
    }
}
