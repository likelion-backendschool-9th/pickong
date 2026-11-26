package com.pickong.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.UserStatus;
import com.pickong.user.domain.vo.Username;
import java.time.OffsetDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UserSanctionRulesTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T00:00:00Z");

    @Test
    void suspendAndBanPreserveProfileAndCounters() {
        User original = active();
        User suspended = original.suspend(NOW.plusHours(168), NOW);
        assertThat(suspended.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(suspended.getSuspendedUntil()).isEqualTo(NOW.plusHours(168));
        assertThat(suspended.getUpdatedAt()).isEqualTo(NOW);
        assertThat(original.getStatus()).isEqualTo(UserStatus.ACTIVE);
        User banned = suspended.ban(NOW.plusHours(1));
        assertThat(banned.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(banned.getSuspendedUntil()).isNull();
        assertThat(banned.canLogin()).isFalse();
        assertThat(active().ban(NOW).getStatus()).isEqualTo(UserStatus.BANNED);
        for (User changed : new User[] {suspended, banned}) {
            assertThat(changed).usingRecursiveComparison()
                    .ignoringFields("status", "suspendedUntil", "updatedAt").isEqualTo(original);
        }
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void loginOnlyActiveAndWarningDoesNotChangeAccount(UserStatus status) {
        User user = active().toBuilder().status(status).build();
        assertThat(user.canLogin()).isEqualTo(status == UserStatus.ACTIVE);
        if (status == UserStatus.ACTIVE || status == UserStatus.SUSPENDED || status == UserStatus.BANNED) {
            user.ensureSanctionAllowed();
            assertThat(user.getStatus()).isEqualTo(status);
        } else {
            assertError(user::ensureSanctionAllowed, UserErrorCode.USER_SANCTION_NOT_ALLOWED);
        }
    }

    @Test
    void activeRestrictionCannotBeExtendedOrDowngraded() {
        User suspended = active().suspend(NOW.plusHours(168), NOW);
        assertError(() -> suspended.suspend(NOW.plusHours(720), NOW.plusHours(1)), UserErrorCode.USER_ALREADY_RESTRICTED);
        User banned = suspended.ban(NOW.plusHours(1));
        assertError(() -> banned.suspend(NOW.plusHours(720), NOW.plusHours(2)), UserErrorCode.USER_ALREADY_RESTRICTED);
        assertError(() -> banned.ban(NOW.plusHours(2)), UserErrorCode.USER_ALREADY_RESTRICTED);
        assertThat(suspended.getSuspendedUntil()).isEqualTo(NOW.plusHours(168));
    }

    @Test
    void expiryBoundaryNeedsHistoryAndAccountNormalizationBeforeLogin() {
        OffsetDateTime until = NOW.plusHours(168);
        User suspended = active().suspend(until, NOW);
        assertThat(suspended.hasExpiredSuspension(until.minusNanos(1))).isFalse();
        assertThat(suspended.hasExpiredSuspension(until)).isTrue();
        assertThat(suspended.hasExpiredSuspension(until.plusNanos(1))).isTrue();
        assertThat(suspended.hasExpiredSuspension(until.withOffsetSameInstant(java.time.ZoneOffset.ofHours(9)))).isTrue();
        assertThat(suspended.canLogin()).isFalse();
        User normalized = suspended.lift(false, until);
        assertThat(normalized.canLogin()).isTrue();
        assertThat(normalized.getSuspendedUntil()).isNull();
        assertThat(normalized.getUpdatedAt()).isEqualTo(until);
        assertThat(suspended.suspend(until.plusHours(720), until).getSuspendedUntil()).isEqualTo(until.plusHours(720));
    }

    @Test
    void missingLegacyDeadlineIsNotAnAutomaticExpiry() {
        User legacy = active().toBuilder().status(UserStatus.SUSPENDED).suspendedUntil(null).build();
        assertThat(legacy.hasExpiredSuspension(NOW)).isFalse();
        assertThat(legacy.canLogin()).isFalse();
        assertError(() -> legacy.suspend(NOW.plusHours(168), NOW), UserErrorCode.USER_ALREADY_RESTRICTED);
        assertThat(legacy.ban(NOW).getStatus()).isEqualTo(UserStatus.BANNED);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "BANNED"})
    void onlyLiftAfterLastEffectiveRestrictionEnds(UserStatus status) {
        User user = active().toBuilder().status(status)
                .suspendedUntil(status == UserStatus.SUSPENDED ? NOW.plusHours(168) : null).build();
        assertThat(user.lift(true, NOW)).isSameAs(user);
        User lifted = user.lift(false, NOW);
        assertThat(lifted.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(lifted.getSuspendedUntil()).isNull();
        assertThat(lifted.lift(false, NOW.plusHours(1))).isSameAs(lifted);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"WITHDRAWN_PENDING", "WITHDRAWN"})
    void sanctionsAndLiftingCannotRestoreWithdrawal(UserStatus status) {
        User withdrawn = active().toBuilder().status(status).withdrawalRequestedAt(NOW.minusDays(1)).build();
        assertError(withdrawn::ensureSanctionAllowed, UserErrorCode.USER_SANCTION_NOT_ALLOWED);
        assertError(() -> withdrawn.suspend(NOW.plusHours(168), NOW), UserErrorCode.USER_SANCTION_NOT_ALLOWED);
        assertError(() -> withdrawn.ban(NOW), UserErrorCode.USER_SANCTION_NOT_ALLOWED);
        assertThat(withdrawn.lift(false, NOW)).isSameAs(withdrawn);
        assertThat(withdrawn.lift(true, NOW)).isSameAs(withdrawn);
    }

    @Test
    void anonymizedAccountCannotBeSanctionedOrRestoredEvenWithInconsistentStatus() {
        User deleted = active().toBuilder().status(UserStatus.BANNED).deletedAt(NOW.minusDays(1)).build();
        assertError(deleted::ensureSanctionAllowed, UserErrorCode.USER_SANCTION_NOT_ALLOWED);
        assertThat(deleted.lift(false, NOW)).isSameAs(deleted);
    }

    @Test
    void bannedAccountCannotWithdrawOrCancelWithdrawal() {
        User banned = active().ban(NOW);
        assertError(() -> banned.requestWithdrawal(NOW), UserErrorCode.WITHDRAWAL_NOT_ALLOWED);
        assertThat(banned.canCancelWithdrawal(NOW)).isFalse();
        assertThat(banned.isDueForAnonymization(NOW.plusYears(1))).isFalse();
        assertError(() -> banned.cancelWithdrawal(NOW), UserErrorCode.WITHDRAWAL_NOT_CANCELLABLE);
        assertThat(UserStatus.valueOf("BANNED")).isEqualTo(UserStatus.BANNED);
    }

    @Test
    void invalidPeriodAndMissingTime() {
        assertError(() -> active().suspend(null, NOW), UserErrorCode.INVALID_SUSPENSION_PERIOD);
        assertError(() -> active().suspend(NOW, NOW), UserErrorCode.INVALID_SUSPENSION_PERIOD);
        assertError(() -> active().suspend(NOW.minusNanos(1), NOW), UserErrorCode.INVALID_SUSPENSION_PERIOD);
        assertThatThrownBy(() -> active().suspend(NOW.plusHours(1), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> active().ban(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> active().lift(false, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> active().hasExpiredSuspension(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(active().hasExpiredSuspension(NOW)).isFalse();
        assertThat(active().ban(NOW).hasExpiredSuspension(NOW.plusYears(1))).isFalse();
    }

    private static User active() {
        return User.register(Username.of("sanction_user"), "사용자", "schema@example.com", null, NOW.minusDays(100))
                .toBuilder().id(42L).bio("소개").feedCount(3L).followerCount(7L).build();
    }

    private static void assertError(ThrowingCallable action, UserErrorCode code) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode()).isEqualTo(code);
    }
}
