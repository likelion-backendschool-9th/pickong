package com.pickong.moderation.application.service;

import com.pickong.admin.application.dto.AdminActionLogEntry;
import com.pickong.admin.application.port.in.AdminActionLogUseCase;
import com.pickong.common.error.BusinessException;
import com.pickong.moderation.application.dto.SanctionCommand;
import com.pickong.moderation.application.port.in.SanctionUseCase;
import com.pickong.moderation.application.port.out.SanctionPersistencePort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.model.Sanction;
import com.pickong.moderation.domain.vo.SanctionType;
import com.pickong.user.application.port.in.UserStatusUseCase;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 잠금 → 제재 이력 → 계정 상태 → 작업 로그 순서로 같은 트랜잭션에서 처리합니다. */
@Service
@Transactional
public class SanctionService implements SanctionUseCase {
    private final SanctionPersistencePort sanctions;
    private final UserStatusUseCase users;
    private final AdminActionLogUseCase logs;
    private final Clock clock;

    public SanctionService(SanctionPersistencePort sanctions, UserStatusUseCase users,
            AdminActionLogUseCase logs, Clock clock) {
        this.sanctions = sanctions;
        this.users = users;
        this.logs = logs;
        this.clock = clock;
    }

    @Override
    public Sanction sanction(SanctionCommand command) {
        users.lockForSanction(command.userId());
        OffsetDateTime now = OffsetDateTime.now(clock);
        Sanction pending = Sanction.create(command.userId(), command.type(), command.reason(), command.suspensionDuration(),
                command.adminId(), command.sourceReportId(), now);
        if (command.sourceReportId() != null && sanctions.findBySourceReportId(command.sourceReportId()).isPresent()) {
            throw new BusinessException(ModerationErrorCode.DUPLICATE_SANCTION_SOURCE);
        }
        normalizeExpiry(command.userId(), now);
        Long supersededId = null;
        if (pending.getType() != SanctionType.WARNING) {
            for (Sanction current : sanctions.findOpenByUserId(command.userId())) {
                if (current.getType() == SanctionType.WARNING) continue;
                if (pending.getType() != SanctionType.BAN || current.getType() != SanctionType.SUSPEND) {
                    throw new BusinessException(ModerationErrorCode.ACTIVE_SANCTION_EXISTS);
                }
                end(current.supersedeByBan(now));
                supersededId = current.getId();
            }
        }
        Sanction saved = sanctions.save(pending);
        switch (saved.getType()) {
            case WARNING -> { /* 계정 이용 상태는 변경하지 않습니다. */ }
            case SUSPEND -> users.suspend(saved.getUserId(), saved.getSuspendedUntil(), now);
            case BAN -> users.ban(saved.getUserId(), now);
        }
        String detail = "{\"sanctionId\":" + saved.getId() + ",\"type\":\"" + saved.getType().name()
                + "\",\"supersededSanctionId\":" + supersededId + "}";
        logs.record(new AdminActionLogEntry(command.adminId(), "SANCTION_USER", "USER", command.userId(),
                saved.getReason(), detail, command.sourceReportId(), now));
        return saved;
    }

    @Override
    public Sanction revoke(long userId, long sanctionId, long adminId, String reason) {
        users.lock(userId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Sanction target = sanctions.findById(sanctionId).filter(value -> value.getUserId() == userId)
                .orElseThrow(() -> new BusinessException(ModerationErrorCode.SANCTION_NOT_FOUND));
        // 만료된 대상의 수동 해제는 도메인 규칙대로 409. 자동 종료는 expireDue가 담당합니다.
        Sanction revoked = target.revoke(adminId, reason, now);
        normalizeExpiry(userId, now);
        end(revoked);
        if (target.getType() != SanctionType.WARNING) {
            users.lift(userId, hasRestriction(userId, now), now);
        }
        logs.record(new AdminActionLogEntry(adminId, "REVOKE_SANCTION", "USER", userId, revoked.getRevokeReason(),
                "{\"sanctionId\":" + sanctionId + "}", null, now));
        return revoked;
    }

    @Override
    public int expireDue(long userId) {
        users.lock(userId);
        return normalizeExpiry(userId, OffsetDateTime.now(clock));
    }

    private int normalizeExpiry(long userId, OffsetDateTime now) {
        int expired = 0;
        for (Sanction current : sanctions.findOpenByUserId(userId)) {
            Sanction ending = current.expireIfDue(now);
            if (ending != current) {
                end(ending);
                expired++;
            }
        }
        users.expireSuspension(userId, hasRestriction(userId, now), now);
        return expired;
    }

    private boolean hasRestriction(long userId, OffsetDateTime now) {
        return sanctions.findOpenByUserId(userId).stream().anyMatch(value -> value.isRestrictionEffectiveAt(now));
    }

    private void end(Sanction ended) {
        if (!sanctions.endIfOpen(ended)) {
            throw new BusinessException(ModerationErrorCode.SANCTION_ALREADY_ENDED);
        }
    }
}
