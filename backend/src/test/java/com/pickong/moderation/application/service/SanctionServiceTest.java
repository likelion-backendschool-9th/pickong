package com.pickong.moderation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pickong.admin.application.dto.AdminActionLogEntry;
import com.pickong.admin.application.port.in.AdminActionLogUseCase;
import com.pickong.common.error.BusinessException;
import com.pickong.moderation.application.dto.SanctionCommand;
import com.pickong.moderation.application.port.out.SanctionPersistencePort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.model.Sanction;
import com.pickong.moderation.domain.vo.SanctionEndReason;
import com.pickong.moderation.domain.vo.SanctionType;
import com.pickong.user.application.port.in.UserStatusUseCase;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SanctionServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    private final SanctionPersistencePort sanctions = mock(SanctionPersistencePort.class);
    private final UserStatusUseCase users = mock(UserStatusUseCase.class);
    private final AdminActionLogUseCase logs = mock(AdminActionLogUseCase.class);
    private final SanctionService service = new SanctionService(sanctions, users, logs, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

    @BeforeEach
    void defaultReads() {
        when(sanctions.findOpenByUserId(1L)).thenReturn(List.of());
    }

    @Test
    void warningDoesNotRestrictAndRecordsOneLog() {
        when(sanctions.save(any())).thenAnswer(call -> ((Sanction) call.getArgument(0)).toBuilder().id(41L).build());
        Sanction saved = service.sanction(command(SanctionType.WARNING, null));
        assertThat(saved.getReason()).isEqualTo("사유");
        verify(users).lockForSanction(1L);
        verify(users, never()).suspend(anyLong(), any(), any());
        verify(users, never()).ban(anyLong(), any());
        ArgumentCaptor<AdminActionLogEntry> entry = ArgumentCaptor.forClass(AdminActionLogEntry.class);
        verify(logs, times(1)).record(entry.capture());
        assertThat(entry.getValue().actionType()).isEqualTo("SANCTION_USER");
        assertThat(entry.getValue().sourceReportId()).isNull();
        assertThat(entry.getValue().detail()).contains("41");
    }

    @Test
    void banClosesSuspensionAndUsesOnlyPublicUserPort() {
        Sanction existing = pending(SanctionType.SUSPEND, NOW.minusHours(1)).toBuilder().id(20L).build();
        when(sanctions.findOpenByUserId(1L)).thenReturn(List.of(existing));
        when(sanctions.endIfOpen(any())).thenReturn(true);
        when(sanctions.save(any())).thenAnswer(call -> ((Sanction) call.getArgument(0)).toBuilder().id(21L).build());
        service.sanction(command(SanctionType.BAN, null));
        ArgumentCaptor<Sanction> closed = ArgumentCaptor.forClass(Sanction.class);
        verify(sanctions).endIfOpen(closed.capture());
        assertThat(closed.getValue().getEndReason()).isEqualTo(SanctionEndReason.SUPERSEDED_BY_BAN);
        var order = inOrder(sanctions, users, logs);
        order.verify(sanctions).endIfOpen(any());
        order.verify(sanctions).save(any());
        order.verify(users).ban(1L, NOW);
        order.verify(logs).record(any());
    }

    @Test
    void activeRestrictionRejectsNewSuspensionWithoutWriting() {
        when(sanctions.findOpenByUserId(1L)).thenReturn(List.of(pending(SanctionType.SUSPEND, NOW)));
        assertCode(() -> service.sanction(command(SanctionType.SUSPEND, null)), ModerationErrorCode.ACTIVE_SANCTION_EXISTS);
        verify(sanctions, never()).save(any());
        verifyNoInteractions(logs);
    }

    @Test
    void invalidReasonAndDuplicateSourceDoNotWrite() {
        assertCode(() -> service.sanction(new SanctionCommand(1, SanctionType.WARNING, " ", null, 2, null)),
                ModerationErrorCode.SANCTION_REASON_REQUIRED);
        when(sanctions.findBySourceReportId(9L)).thenReturn(Optional.of(pending(SanctionType.WARNING, NOW)));
        assertCode(() -> service.sanction(command(SanctionType.WARNING, 9L)), ModerationErrorCode.DUPLICATE_SANCTION_SOURCE);
        verify(sanctions, never()).save(any());
        verify(sanctions, never()).endIfOpen(any());
        verifyNoInteractions(logs);
    }

    @Test
    void expireDueClosesHistoryAtOriginalDeadlineWithoutFakeAdminLog() {
        Sanction overdue = pending(SanctionType.SUSPEND, NOW.minusDays(8)).toBuilder().id(41L).build();
        when(sanctions.findOpenByUserId(1L)).thenReturn(List.of(overdue));
        when(sanctions.endIfOpen(any())).thenReturn(true);
        assertThat(service.expireDue(1L)).isEqualTo(1);
        ArgumentCaptor<Sanction> closed = ArgumentCaptor.forClass(Sanction.class);
        verify(sanctions).endIfOpen(closed.capture());
        assertThat(closed.getValue().getEndedAt()).isEqualTo(overdue.getSuspendedUntil());
        assertThat(closed.getValue().getEndReason()).isEqualTo(SanctionEndReason.EXPIRED);
        verify(users).expireSuspension(1L, false, NOW);
        verifyNoInteractions(logs);
    }

    @Test
    void revokeChecksOwnershipAndEndedStateBeforeWriting() {
        Sanction other = Sanction.create(3, SanctionType.BAN, "사유", null, 2, null, NOW).toBuilder().id(41L).build();
        when(sanctions.findById(41L)).thenReturn(Optional.of(other));
        assertCode(() -> service.revoke(1, 41, 2, null), ModerationErrorCode.SANCTION_NOT_FOUND);
        Sanction ended = pending(SanctionType.BAN, NOW).toBuilder().id(41L).build().revoke(2, null, NOW);
        when(sanctions.findById(41L)).thenReturn(Optional.of(ended));
        assertCode(() -> service.revoke(1, 41, 2, null), ModerationErrorCode.SANCTION_ALREADY_ENDED);
        verify(sanctions, never()).endIfOpen(any());
        verifyNoInteractions(logs);
    }

    @Test
    void revokePreservesOtherEffectiveRestriction() {
        Sanction target = pending(SanctionType.BAN, NOW).toBuilder().id(41L).build();
        when(sanctions.findById(41L)).thenReturn(Optional.of(target));
        when(sanctions.findOpenByUserId(1L)).thenReturn(List.of(pending(SanctionType.SUSPEND, NOW)));
        when(sanctions.endIfOpen(any())).thenReturn(true);
        Sanction result = service.revoke(1, 41, 2, " 확인 ");
        assertThat(result.getRevokedByAdminId()).isEqualTo(2L);
        verify(users).lift(1L, true, NOW);
        verify(logs).record(any());
    }

    @Test
    void expiredRevokeAndFailedEndingDoNotCreateLog() {
        when(sanctions.findById(41L)).thenReturn(Optional.of(pending(SanctionType.SUSPEND, NOW.minusDays(8)).toBuilder().id(41L).build()));
        assertCode(() -> service.revoke(1, 41, 2, null), ModerationErrorCode.SANCTION_ALREADY_EXPIRED);
        when(sanctions.findById(41L)).thenReturn(Optional.of(pending(SanctionType.BAN, NOW).toBuilder().id(41L).build()));
        assertCode(() -> service.revoke(1, 41, 2, null), ModerationErrorCode.SANCTION_ALREADY_ENDED);
        verifyNoInteractions(logs);
    }

    private SanctionCommand command(SanctionType type, Long report) {
        return new SanctionCommand(1, type, " 사유 ", type == SanctionType.SUSPEND ? Sanction.SEVEN_DAYS : null, 2, report);
    }

    private Sanction pending(SanctionType type, OffsetDateTime at) {
        return Sanction.create(1, type, "사유", type == SanctionType.SUSPEND ? Sanction.SEVEN_DAYS : null, 2, null, at);
    }

    private static void assertCode(ThrowingCallable action, ModerationErrorCode code) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
    }
}
