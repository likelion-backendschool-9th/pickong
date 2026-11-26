package com.pickong.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pickong.common.error.BusinessException;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.UserErrorCode;
import com.pickong.user.domain.event.UserStatusChangedEvent;
import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.UserStatus;
import com.pickong.user.domain.vo.Username;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class UserStatusServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    private final UserPersistencePort users = mock(UserPersistencePort.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UserStatusService service = new UserStatusService(users, events);

    @Test
    void changedStatusIsStoredBeforeEvent() {
        User original = active();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(original));
        when(users.updateStatusIfUnchanged(any(), any())).thenReturn(true);
        service.suspend(1, NOW.plusHours(168), NOW);
        ArgumentCaptor<User> changed = ArgumentCaptor.forClass(User.class);
        var order = inOrder(users, events);
        order.verify(users).updateStatusIfUnchanged(eq(original), changed.capture());
        order.verify(events).publishEvent(new UserStatusChangedEvent(1, "SUSPENDED", NOW));
        assertThat(changed.getValue().getSuspendedUntil()).isEqualTo(NOW.plusHours(168));
    }

    @Test
    void concurrentStatusConflictDoesNotPublishEvent() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(active()));
        assertThatThrownBy(() -> service.ban(1, NOW)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(UserErrorCode.USER_STATUS_CONFLICT);
        verifyNoInteractions(events);
    }

    @Test
    void remainingRestrictionAndWithdrawalNeverRestoreAccount() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(active().ban(NOW)));
        service.lift(1, true, NOW);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(active().requestWithdrawal(NOW)));
        service.lift(1, false, NOW);
        verify(users, never()).updateStatusIfUnchanged(any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void expiryNeverLiftsBanButRestoresElapsedSuspension() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(active().ban(NOW)));
        service.expireSuspension(1, false, NOW.plusDays(100));
        verify(users, never()).updateStatusIfUnchanged(any(), any());
        User suspended = active().suspend(NOW.plusHours(168), NOW);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(suspended));
        when(users.updateStatusIfUnchanged(any(), any())).thenReturn(true);
        service.expireSuspension(1, false, NOW.plusHours(168));
        verify(events).publishEvent(new UserStatusChangedEvent(1, "ACTIVE", NOW.plusHours(168)));
    }

    @Test
    void missingAndWithdrawnAccountsCannotReceiveNewSanction() {
        assertThatThrownBy(() -> service.lock(1)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(active().requestWithdrawal(NOW)));
        assertThatThrownBy(() -> service.lockForSanction(1)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(UserErrorCode.USER_SANCTION_NOT_ALLOWED);
        verifyNoInteractions(events);
    }

    private User active() {
        return User.register(Username.of("sanction_test"), null, null, null, NOW.minusDays(1)).toBuilder().id(1L).build();
    }
}
