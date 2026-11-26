package com.pickong.user.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.user.application.port.in.UserStatusUseCase;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.event.UserStatusChangedEvent;
import com.pickong.user.domain.model.User;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class UserStatusService implements UserStatusUseCase {
    private final UserPersistencePort users;
    private final ApplicationEventPublisher events;

    public UserStatusService(UserPersistencePort users, ApplicationEventPublisher events) {
        this.users = users;
        this.events = events;
    }

    @Override
    public void lock(long userId) {
        load(userId);
    }

    @Override
    public void lockForSanction(long userId) {
        load(userId).ensureSanctionAllowed();
    }

    @Override
    public void suspend(long userId, OffsetDateTime until, OffsetDateTime now) {
        User previous = load(userId);
        save(previous, previous.suspend(until, now));
    }

    @Override
    public void ban(long userId, OffsetDateTime now) {
        User previous = load(userId);
        save(previous, previous.ban(now));
    }

    @Override
    public void lift(long userId, boolean hasRemainingRestriction, OffsetDateTime now) {
        User previous = load(userId);
        save(previous, previous.lift(hasRemainingRestriction, now));
    }

    @Override
    public void expireSuspension(long userId, boolean hasRemainingRestriction, OffsetDateTime now) {
        User previous = load(userId);
        if (previous.hasExpiredSuspension(now)) {
            save(previous, previous.lift(hasRemainingRestriction, now));
        }
    }

    private User load(long userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private void save(User previous, User updated) {
        if (previous == updated) return;
        if (!users.updateStatusIfUnchanged(previous, updated)) {
            throw new BusinessException(UserErrorCode.USER_STATUS_CONFLICT);
        }
        events.publishEvent(new UserStatusChangedEvent(updated.getId(), updated.getStatus().name(), updated.getUpdatedAt()));
    }
}
