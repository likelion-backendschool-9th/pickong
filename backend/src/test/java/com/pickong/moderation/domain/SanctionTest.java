package com.pickong.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.domain.model.Sanction;
import com.pickong.moderation.domain.vo.SanctionEndReason;
import com.pickong.moderation.domain.vo.SanctionType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** DB나 Spring 없이 제재 생성·시간 경계·종료 이력 규칙을 검증합니다. */
class SanctionTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T12:00:00+09:00");

    @ParameterizedTest
    @ValueSource(longs = {168, 720})
    void suspensionUsesExactHours(long hours) {
        Sanction sanction = create(SanctionType.SUSPEND, Duration.ofHours(hours));

        assertThat(Duration.between(sanction.getCreatedAt(), sanction.getSuspendedUntil())).isEqualTo(Duration.ofHours(hours));
        assertThat(sanction.getSourceReportId()).isEqualTo(9L);
        assertThat(sanction.isEffectiveAt(NOW.minusNanos(1))).isFalse();
        assertThat(sanction.isRestrictionEffectiveAt(NOW)).isTrue();
        assertThat(sanction.isEffectiveAt(sanction.getSuspendedUntil().minusNanos(1))).isTrue();
        assertThat(sanction.isEffectiveAt(sanction.getSuspendedUntil())).isFalse();
        assertThat(sanction.isEffectiveAt(sanction.getSuspendedUntil().plusNanos(1))).isFalse();
        assertThat(sanction.isEffectiveAt(sanction.getSuspendedUntil().withOffsetSameInstant(java.time.ZoneOffset.UTC))).isFalse();
    }

    @Test
    void elapsedHoursAcrossOffsetChange() {
        OffsetDateTime start = OffsetDateTime.parse("2026-03-07T12:00:00-05:00");
        Sanction sanction = Sanction.create(1L, SanctionType.SUSPEND, "사유", Sanction.SEVEN_DAYS, 2L, null, start);
        OffsetDateTime finish = sanction.getSuspendedUntil().withOffsetSameInstant(java.time.ZoneOffset.ofHours(-4));

        assertThat(Duration.between(start, finish)).isEqualTo(Duration.ofHours(168));
        assertThat(finish.getHour()).isEqualTo(13);
    }

    @ParameterizedTest
    @EnumSource(value = SanctionType.class, names = {"WARNING", "BAN"})
    void noExpiryForWarningAndBan(SanctionType type) {
        Sanction sanction = create(type, null);
        assertThat(sanction.getSuspendedUntil()).isNull();
        assertThat(sanction.isEffectiveAt(NOW.plusYears(100))).isTrue();
        assertThat(sanction.isRestrictionEffectiveAt(NOW)).isEqualTo(type == SanctionType.BAN);
        assertThat(sanction.expireIfDue(NOW.plusYears(100))).isSameAs(sanction);
        assertError(() -> create(type, Sanction.SEVEN_DAYS), ModerationErrorCode.INVALID_SANCTION_PERIOD);
    }

    @ParameterizedTest
    @ValueSource(longs = {-168, 0, 24, 169, 719, 721})
    void unsupportedSuspensionDuration(long hours) {
        assertError(() -> create(SanctionType.SUSPEND, Duration.ofHours(hours)), ModerationErrorCode.INVALID_SANCTION_PERIOD);
    }

    @Test
    void suspensionNeedsDurationAndKnownType() {
        assertError(() -> create(SanctionType.SUSPEND, null), ModerationErrorCode.INVALID_SANCTION_PERIOD);
        assertError(() -> create(null, null), ModerationErrorCode.INVALID_SANCTION_TYPE);
    }

    @Test
    void directRepeatedWarningsAreIndependentHistory() {
        Sanction first = Sanction.create(1L, SanctionType.WARNING, "첫 경고", null, 2L, null, NOW);
        Sanction second = Sanction.create(1L, SanctionType.WARNING, "두 번째 경고", null, 2L, null, NOW.plusHours(1));
        assertThat(first.getReason()).isEqualTo("첫 경고");
        assertThat(second.getReason()).isEqualTo("두 번째 경고");
        assertThat(first.getSourceReportId()).isNull();
        assertThat(first.isRestrictionEffectiveAt(NOW)).isFalse();
        assertThat(second.isEffectiveAt(NOW.plusHours(1))).isTrue();
    }

    @Test
    void mandatoryReasonAndCodePointLimit() {
        assertError(() -> warning(null), ModerationErrorCode.SANCTION_REASON_REQUIRED);
        assertError(() -> warning(""), ModerationErrorCode.SANCTION_REASON_REQUIRED);
        assertThat(warning("  줄 1\n줄 2  ").getReason()).isEqualTo("줄 1\n줄 2");
        assertThat(warning("😀".repeat(200)).getReason()).isEqualTo("😀".repeat(200));
        assertError(() -> warning("😀".repeat(201)), ModerationErrorCode.SANCTION_REASON_TOO_LONG);
    }

    static IntStream reasonSpaces() {
        return IntStream.of(9, 10, 11, 12, 13, 28, 29, 30, 31, 32, 0x85, 0xa0, 0x1680,
                0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008,
                0x2009, 0x200a, 0x2028, 0x2029, 0x202f, 0x205f, 0x3000);
    }

    @ParameterizedTest
    @MethodSource("reasonSpaces")
    void reasonWhitespaceMatchesV15(int codePoint) {
        String space = Character.toString(codePoint);
        assertError(() -> warning(space.repeat(3)), ModerationErrorCode.SANCTION_REASON_REQUIRED);
        assertThat(warning(space + "사유" + space).getReason()).isEqualTo("사유");
        assertThat(create(SanctionType.BAN, null).revoke(3L, space, NOW).getRevokeReason()).isNull();
    }

    @Test
    void expiryIsIdempotentAndRecordsOriginalDeadline() {
        Sanction original = create(SanctionType.SUSPEND, Sanction.SEVEN_DAYS);
        OffsetDateTime until = original.getSuspendedUntil();
        assertThat(original.expireIfDue(until.minusNanos(1))).isSameAs(original);
        Sanction expired = original.expireIfDue(until);
        assertThat(expired.getEndedAt()).isEqualTo(until);
        assertThat(expired.getEndReason()).isEqualTo(SanctionEndReason.EXPIRED);
        assertThat(expired.getRevokedAt()).isNull();
        assertThat(expired.getRevokedByAdminId()).isNull();
        assertThat(expired.getRevokeReason()).isNull();
        assertThat(original.expireIfDue(until.plusDays(10)).getEndedAt()).isEqualTo(until);
        assertThat(expired.expireIfDue(until.plusDays(10))).isSameAs(expired);
        assertThat(original.getEndedAt()).isNull();
        assertOriginalFieldsPreserved(original, expired);
    }

    @Test
    void manualRevocationPreservesEvidenceAndRejectsReplay() {
        Sanction original = create(SanctionType.SUSPEND, Sanction.THIRTY_DAYS).toBuilder().id(42L).build();
        OffsetDateTime revokedAt = NOW.plusHours(1);
        Sanction revoked = original.revoke(3L, "  오처리 정정  ", revokedAt);
        assertThat(revoked.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(revoked.getEndedAt()).isEqualTo(revokedAt);
        assertThat(revoked.getEndReason()).isEqualTo(SanctionEndReason.REVOKED);
        assertThat(revoked.getRevokedByAdminId()).isEqualTo(3L);
        assertThat(revoked.getRevokeReason()).isEqualTo("오처리 정정");
        assertThat(revoked.isEffectiveAt(revokedAt)).isFalse();
        assertThat(revoked.expireIfDue(NOW.plusDays(100))).isSameAs(revoked);
        assertThat(original.getRevokedAt()).isNull();
        assertOriginalFieldsPreserved(original, revoked);
        assertError(() -> revoked.revoke(4L, "재해제", revokedAt), ModerationErrorCode.SANCTION_ALREADY_ENDED);
        assertError(() -> revoked.supersedeByBan(revokedAt), ModerationErrorCode.SANCTION_ALREADY_ENDED);
    }

    @Test
    void optionalRevokeReasonUsesSameLimit() {
        Sanction ban = create(SanctionType.BAN, null);
        assertThat(ban.revoke(3L, null, NOW).getRevokeReason()).isNull();
        assertThat(ban.revoke(3L, "😀".repeat(200), NOW).getRevokeReason()).isEqualTo("😀".repeat(200));
        assertError(() -> ban.revoke(3L, "😀".repeat(201), NOW), ModerationErrorCode.SANCTION_REASON_TOO_LONG);
        assertThat(ban.getEndedAt()).isNull();
    }

    @Test
    void banConversionIsDistinctFromRevocation() {
        Sanction original = create(SanctionType.SUSPEND, Sanction.SEVEN_DAYS);
        Sanction superseded = original.supersedeByBan(NOW.plusHours(1));
        assertThat(superseded.getEndReason()).isEqualTo(SanctionEndReason.SUPERSEDED_BY_BAN);
        assertThat(superseded.getEndedAt()).isEqualTo(NOW.plusHours(1));
        assertThat(superseded.getRevokedAt()).isNull();
        assertThat(superseded.getRevokedByAdminId()).isNull();
        assertThat(superseded.getRevokeReason()).isNull();
        assertThat(original.getEndedAt()).isNull();
        assertOriginalFieldsPreserved(original, superseded);
        assertError(() -> create(SanctionType.WARNING, null).supersedeByBan(NOW), ModerationErrorCode.INVALID_SANCTION_TRANSITION);
        assertError(() -> create(SanctionType.BAN, null).supersedeByBan(NOW), ModerationErrorCode.INVALID_SANCTION_TRANSITION);
    }

    @Test
    void overdueSuspensionMustExpireBeforeOtherTransitions() {
        Sanction original = create(SanctionType.SUSPEND, Sanction.SEVEN_DAYS);
        OffsetDateTime until = original.getSuspendedUntil();
        assertError(() -> original.revoke(3L, null, until), ModerationErrorCode.SANCTION_ALREADY_EXPIRED);
        assertError(() -> original.supersedeByBan(until), ModerationErrorCode.SANCTION_ALREADY_EXPIRED);
        Sanction expired = original.expireIfDue(until);
        assertError(() -> expired.revoke(3L, null, until), ModerationErrorCode.SANCTION_ALREADY_ENDED);
    }

    @Test
    void invalidInternalIdentifiersAndTimes() {
        assertThatThrownBy(() -> Sanction.create(0L, SanctionType.WARNING, "사유", null, 2L, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.create(1L, SanctionType.WARNING, "사유", null, 0L, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.create(1L, SanctionType.WARNING, "사유", null, 2L, -1L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.create(1L, SanctionType.WARNING, "사유", null, 2L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        Sanction ban = create(SanctionType.BAN, null);
        assertThatThrownBy(() -> ban.revoke(0L, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ban.revoke(3L, null, NOW.minusNanos(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ban.isEffectiveAt(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Sanction create(SanctionType type, Duration duration) {
        return Sanction.create(1L, type, "원래 사유", duration, 2L, 9L, NOW);
    }

    private static Sanction warning(String reason) {
        return Sanction.create(1L, SanctionType.WARNING, reason, null, 2L, null, NOW);
    }

    private static void assertOriginalFieldsPreserved(Sanction original, Sanction changed) {
        assertThat(changed).usingRecursiveComparison()
                .ignoringFields("endedAt", "endReason", "revokedAt", "revokedByAdminId", "revokeReason")
                .isEqualTo(original);
    }

    private static void assertError(ThrowingCallable action, ModerationErrorCode code) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode()).isEqualTo(code);
    }
}
