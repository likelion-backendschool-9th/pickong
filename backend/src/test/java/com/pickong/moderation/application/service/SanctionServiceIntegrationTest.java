package com.pickong.moderation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.event.UserStatusChangedListener;
import com.pickong.common.error.BusinessException;
import com.pickong.moderation.application.dto.SanctionCommand;
import com.pickong.moderation.application.port.in.SanctionUseCase;
import com.pickong.moderation.application.port.out.SanctionPersistencePort;
import com.pickong.moderation.domain.model.Sanction;
import com.pickong.moderation.domain.vo.SanctionEndReason;
import com.pickong.moderation.domain.vo.SanctionType;
import com.pickong.user.application.port.in.UserStatusUseCase;
import com.pickong.user.application.port.in.UserWithdrawalUseCase;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.event.UserStatusChangedEvent;
import com.pickong.user.domain.vo.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SanctionServiceIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    @MockitoBean Clock clock;
    @Autowired SanctionUseCase service;
    @Autowired SanctionPersistencePort sanctions;
    @Autowired UserPersistencePort users;
    @Autowired UserStatusUseCase statuses;
    @Autowired UserWithdrawalUseCase withdrawals;
    @Autowired UserStatusChangedListener tokenListener;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    private long userId;
    private long otherUserId;
    private long adminId;
    private long reviewerId;
    private long reportId;
    private String withdrawalDetail;

    @BeforeEach
    void fixtures() {
        at(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        // 탈퇴가 먼저 성공하면 한국 날짜 계산을 위해 withZone을 사용합니다. 실제 Clock처럼 동작시킵니다.
        given(clock.withZone(any(ZoneId.class))).willAnswer(call -> Clock.fixed(clock.instant(), call.getArgument(0, ZoneId.class)));
        userId = insertUser();
        otherUserId = insertUser();
        adminId = insertAdmin();
        reviewerId = insertAdmin();
        reportId = jdbc.queryForObject("""
                INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot)
                VALUES (?,'USER',?,'SPAM','original-snapshot') RETURNING id
                """, Long.class, otherUserId, userId);
        withdrawalDetail = "s6-" + UUID.randomUUID();
        for (int index = 0; index < 2; index++) {
            jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,family,expires_at) VALUES (?,?,?,?)",
                    userId, "s6-" + UUID.randomUUID(), "family-" + index, NOW.plusDays(60));
        }
    }

    @AfterEach
    void cleanOnlyOwnData() {
        jdbc.execute("ALTER TABLE admin_action_logs DROP CONSTRAINT IF EXISTS ck_s6_reject_reason");
        jdbc.execute("ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS ck_s6_reject_revocation");
        jdbc.update("DELETE FROM admin_action_logs WHERE admin_id IN (?,?)", adminId, reviewerId);
        jdbc.update("DELETE FROM user_sanctions WHERE user_id IN (?,?)", userId, otherUserId);
        jdbc.update("DELETE FROM reports WHERE id=?", reportId);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (?,?)", userId, otherUserId);
        jdbc.update("DELETE FROM withdrawal_reasons WHERE detail=?", withdrawalDetail);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", userId, otherUserId);
        jdbc.update("DELETE FROM admins WHERE id IN (?,?)", adminId, reviewerId);
    }

    @Test
    void repeatedWarningsKeepAccountAndSessionsAndEachHaveOneLog() {
        Sanction first = service.sanction(command(userId, SanctionType.WARNING, null));
        Sanction second = service.sanction(command(userId, SanctionType.WARNING, null));
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertStatus(UserStatus.ACTIVE);
        assertThat(liveTokens()).isEqualTo(2);
        assertThat(sanctions.findOpenByUserId(userId)).hasSize(2);
        assertThat(logCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {168, 720})
    void suspensionStoresExactPeriodAndRevokesEverySessionAfterCommit(long hours) {
        Sanction saved = service.sanction(new SanctionCommand(userId, SanctionType.SUSPEND, " 사유 ", Duration.ofHours(hours), adminId, null));
        assertStatus(UserStatus.SUSPENDED);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getSuspendedUntil()).isEqualTo(NOW.plusHours(hours));
        assertThat(users.findById(userId).orElseThrow().getSuspendedUntil()).isEqualTo(saved.getSuspendedUntil());
        assertThat(liveTokens()).isZero();
        assertThat(logCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id=?", String.class, reportId)).isEqualTo("PENDING");
    }

    @Test
    void revocationIsDeferredUntilOuterCommitAndSkippedOnRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.sanction(command(userId, SanctionType.BAN, null));
            assertThat(liveTokens()).isEqualTo(2);
            status.setRollbackOnly();
        });
        assertStatus(UserStatus.ACTIVE);
        assertThat(liveTokens()).isEqualTo(2);
        assertThat(sanctions.findByUserId(userId)).isEmpty();
        assertThat(logCount()).isZero();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.sanction(command(userId, SanctionType.BAN, null));
            assertThat(liveTokens()).isEqualTo(2);
        });
        assertStatus(UserStatus.BANNED);
        assertThat(liveTokens()).isZero();
    }

    @Test
    void activeRestrictionConflictsDoNotExtendOrAddLogs() {
        Sanction first = service.sanction(command(userId, SanctionType.SUSPEND, null));
        assertCode(() -> service.sanction(command(userId, SanctionType.SUSPEND, null)), "ACTIVE_SANCTION_EXISTS");
        assertThat(sanctions.findByUserId(userId)).hasSize(1);
        assertThat(users.findById(userId).orElseThrow().getSuspendedUntil()).isEqualTo(first.getSuspendedUntil());
        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void banClosesOldSuspensionAndDoesNotDuplicateItsLog() {
        Sanction suspension = service.sanction(command(userId, SanctionType.SUSPEND, null));
        at(NOW.plusHours(1));
        Sanction ban = service.sanction(command(userId, SanctionType.BAN, reportId));
        Sanction previous = sanctions.findById(suspension.getId()).orElseThrow();
        assertThat(previous.getEndReason()).isEqualTo(SanctionEndReason.SUPERSEDED_BY_BAN);
        assertThat(previous.getRevokedAt()).isNull();
        assertThat(previous.getSuspendedUntil()).isEqualTo(suspension.getSuspendedUntil());
        assertThat(previous.getReason()).isEqualTo(suspension.getReason());
        assertThat(sanctions.findOpenByUserId(userId)).extracting(Sanction::getId).containsExactly(ban.getId());
        assertStatus(UserStatus.BANNED);
        assertThat(users.findById(userId).orElseThrow().getSuspendedUntil()).isNull();
        assertThat(logCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_action_logs WHERE source_report_id=?", Long.class, reportId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE id=?", String.class, reportId)).isEqualTo("original-snapshot");
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id=?", String.class, reportId)).isEqualTo("PENDING");
        assertCode(() -> service.sanction(command(userId, SanctionType.BAN, null)), "ACTIVE_SANCTION_EXISTS");
        assertCode(() -> service.sanction(command(userId, SanctionType.SUSPEND, null)), "ACTIVE_SANCTION_EXISTS");
        service.sanction(command(userId, SanctionType.WARNING, null));
        assertStatus(UserStatus.BANNED);
        assertThat(logCount()).isEqualTo(3);
    }

    @Test
    void expiryHasExactBoundaryPreservesHistoryAndCreatesNoFakeAdminLog() {
        Sanction saved = service.sanction(command(userId, SanctionType.SUSPEND, null));
        at(saved.getSuspendedUntil().minusNanos(1));
        assertThat(service.expireDue(userId)).isZero();
        assertStatus(UserStatus.SUSPENDED);
        at(saved.getSuspendedUntil());
        assertThat(service.expireDue(userId)).isEqualTo(1);
        assertStatus(UserStatus.ACTIVE);
        Sanction expired = sanctions.findById(saved.getId()).orElseThrow();
        assertThat(expired.getEndReason()).isEqualTo(SanctionEndReason.EXPIRED);
        assertThat(expired.getEndedAt()).isEqualTo(saved.getSuspendedUntil());
        assertThat(expired.getRevokedAt()).isNull();
        assertThat(expired.getRevokedByAdminId()).isNull();
        assertThat(expired.getReason()).isEqualTo(saved.getReason());
        assertThat(service.expireDue(userId)).isZero();
        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void newRestrictionFirstClosesOverdueOpenRow() {
        Sanction saved = service.sanction(command(userId, SanctionType.SUSPEND, null));
        at(saved.getSuspendedUntil());
        Sanction next = service.sanction(command(userId, SanctionType.SUSPEND, null));
        assertThat(sanctions.findById(saved.getId()).orElseThrow().getEndReason()).isEqualTo(SanctionEndReason.EXPIRED);
        assertThat(next.getCreatedAt()).isEqualTo(saved.getSuspendedUntil());
        assertStatus(UserStatus.SUSPENDED);
        assertThat(sanctions.findByUserId(userId)).hasSize(2);
        assertThat(logCount()).isEqualTo(2);
    }

    @Test
    void revokeTargetsOneHistoryKeepsWarningsAndRejectsReplayAndWrongOwner() {
        Sanction warning = service.sanction(command(userId, SanctionType.WARNING, null));
        Sanction ban = service.sanction(command(userId, SanctionType.BAN, null));
        assertCode(() -> service.revoke(otherUserId, ban.getId(), reviewerId, null), "SANCTION_NOT_FOUND");
        Sanction revoked = service.revoke(userId, ban.getId(), reviewerId, " 해제 확인 ");
        assertStatus(UserStatus.ACTIVE);
        assertThat(revoked.getRevokedByAdminId()).isEqualTo(reviewerId);
        assertThat(revoked.getRevokeReason()).isEqualTo("해제 확인");
        assertThat(sanctions.findOpenByUserId(userId)).extracting(Sanction::getId).containsExactly(warning.getId());
        assertCode(() -> service.revoke(userId, ban.getId(), adminId, null), "SANCTION_ALREADY_ENDED");
        assertThat(logCount()).isEqualTo(3);
        assertThat(liveTokens()).isZero();
    }

    @Test
    void sourceRemainsUniqueAfterRevokeAndDirectRevokeHasNoReportOrigin() {
        Sanction original = service.sanction(command(userId, SanctionType.BAN, reportId));
        service.revoke(userId, original.getId(), reviewerId, null);
        assertCode(() -> service.sanction(command(userId, SanctionType.BAN, reportId)), "DUPLICATE_SANCTION_SOURCE");
        assertCode(() -> service.sanction(command(otherUserId, SanctionType.WARNING, reportId)), "DUPLICATE_SANCTION_SOURCE");
        assertThat(logCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT source_report_id FROM admin_action_logs WHERE action_type='REVOKE_SANCTION' AND target_id=?", Long.class, userId)).isNull();
    }

    @Test
    void withdrawnAccountsCannotReceiveAnySanctionOrBeRestoredByOldRevoke() {
        Sanction original = service.sanction(command(userId, SanctionType.BAN, null));
        for (String status : List.of("WITHDRAWN_PENDING", "WITHDRAWN")) {
            jdbc.update("UPDATE users SET status=? WHERE id=?", status, userId);
            assertCode(() -> service.sanction(command(userId, SanctionType.WARNING, null)), "USER_SANCTION_NOT_ALLOWED");
            assertCode(() -> service.sanction(command(userId, SanctionType.SUSPEND, null)), "USER_SANCTION_NOT_ALLOWED");
            assertCode(() -> service.sanction(command(userId, SanctionType.BAN, null)), "USER_SANCTION_NOT_ALLOWED");
            assertThat(service.expireDue(userId)).isZero();
        }
        service.revoke(userId, original.getId(), reviewerId, null);
        assertStatus(UserStatus.WITHDRAWN);
        assertThat(logCount()).isEqualTo(2);
        assertCode(() -> service.sanction(command(Long.MAX_VALUE, SanctionType.WARNING, null)), "USER_NOT_FOUND");
    }

    @Test
    void expiredRevokeIsConflictAndExplicitExpiryPreservesAutomaticEnding() {
        Sanction saved = service.sanction(command(userId, SanctionType.SUSPEND, null));
        at(saved.getSuspendedUntil());
        assertCode(() -> service.revoke(userId, saved.getId(), reviewerId, null), "SANCTION_ALREADY_EXPIRED");
        assertThat(service.expireDue(userId)).isEqualTo(1);
        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void logFailureRollsBackAccountHistoryAndDoesNotRevokeTokens() {
        rejectLogReason();
        assertThatThrownBy(() -> service.sanction(new SanctionCommand(userId, SanctionType.BAN, "s6-rollback", null, adminId, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertStatus(UserStatus.ACTIVE);
        assertThat(sanctions.findByUserId(userId)).isEmpty();
        assertThat(logCount()).isZero();
        assertThat(liveTokens()).isEqualTo(2);
    }

    @Test
    void failedBanUpgradeRestoresOriginalOpenSuspension() {
        Sanction previous = service.sanction(command(userId, SanctionType.SUSPEND, null));
        rejectLogReason();
        assertThatThrownBy(() -> service.sanction(new SanctionCommand(userId, SanctionType.BAN, "s6-rollback", null, adminId, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertStatus(UserStatus.SUSPENDED);
        assertThat(sanctions.findByUserId(userId)).hasSize(1);
        assertThat(sanctions.findById(previous.getId()).orElseThrow().getEndedAt()).isNull();
        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void afterCommitFailureCannotRollbackSanctionAndCanBeRetriedIdempotently() {
        jdbc.execute("ALTER TABLE refresh_tokens ADD CONSTRAINT ck_s6_reject_revocation CHECK (revoked_at IS NULL)");
        service.sanction(command(userId, SanctionType.BAN, null));
        assertStatus(UserStatus.BANNED);
        assertThat(sanctions.findByUserId(userId)).hasSize(1);
        assertThat(logCount()).isEqualTo(1);
        assertThat(liveTokens()).isEqualTo(2);
        jdbc.execute("ALTER TABLE refresh_tokens DROP CONSTRAINT ck_s6_reject_revocation");
        UserStatusChangedEvent event = new UserStatusChangedEvent(userId, "BANNED", NOW);
        tokenListener.on(event);
        assertThat(liveTokens()).isZero();
        tokenListener.on(event);
        assertThat(liveTokens()).isZero();
        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void userStatePortCannotCommitIndependently() {
        assertThatThrownBy(() -> statuses.ban(userId, NOW)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void concurrentSuspensionsAndRevocationsHaveOneWinner() throws Exception {
        List<String> results = race(() -> outcome(() -> service.sanction(command(userId, SanctionType.SUSPEND, null))),
                () -> outcome(() -> service.sanction(command(userId, SanctionType.SUSPEND, null))));
        assertThat(results).containsExactlyInAnyOrder("OK", "ACTIVE_SANCTION_EXISTS");
        Sanction target = sanctions.findOpenByUserId(userId).getFirst();
        List<String> revokeResults = race(() -> outcome(() -> service.revoke(userId, target.getId(), adminId, null)),
                () -> outcome(() -> service.revoke(userId, target.getId(), reviewerId, null)));
        assertThat(revokeResults).containsExactlyInAnyOrder("OK", "SANCTION_ALREADY_ENDED");
        assertStatus(UserStatus.ACTIVE);
        assertThat(logCount()).isEqualTo(2);
    }

    @Test
    void withdrawalAndSanctionUseSameUserLock() throws Exception {
        List<String> results = race(() -> outcome(() -> service.sanction(command(userId, SanctionType.BAN, null))),
                () -> outcome(() -> { withdrawals.requestWithdrawal(userId, "ETC", withdrawalDetail); return null; }));
        assertThat(results).contains("OK");
        if (users.findById(userId).orElseThrow().getStatus() == UserStatus.BANNED) {
            assertThat(results).containsExactlyInAnyOrder("OK", "WITHDRAWAL_NOT_ALLOWED");
            assertThat(logCount()).isEqualTo(1);
        } else {
            assertStatus(UserStatus.WITHDRAWN_PENDING);
            assertThat(results).containsExactlyInAnyOrder("OK", "USER_SANCTION_NOT_ALLOWED");
            assertThat(sanctions.findByUserId(userId)).isEmpty();
            assertThat(logCount()).isZero();
        }
    }

    @Test
    void withdrawalBeforeSanctionPreservesWithdrawalAndRejectsNewSanction() {
        OffsetDateTime requestedAt = NOW.plusHours(15); // 한국에서는 다음 날짜입니다.
        at(requestedAt);
        withdrawals.requestWithdrawal(userId, "ETC", withdrawalDetail);
        assertCode(() -> service.sanction(command(userId, SanctionType.BAN, null)), "USER_SANCTION_NOT_ALLOWED");
        assertStatus(UserStatus.WITHDRAWN_PENDING);
        assertThat(sanctions.findByUserId(userId)).isEmpty();
        assertThat(logCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawal_reasons WHERE detail=?", Long.class, withdrawalDetail)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT requested_on FROM withdrawal_reasons WHERE detail=?", java.time.LocalDate.class, withdrawalDetail))
                .isEqualTo(requestedAt.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate());
    }

    private void rejectLogReason() {
        jdbc.execute("ALTER TABLE admin_action_logs ADD CONSTRAINT ck_s6_reject_reason CHECK (reason IS DISTINCT FROM 's6-rollback')");
    }

    private SanctionCommand command(long id, SanctionType type, Long source) {
        return new SanctionCommand(id, type, "사유", type == SanctionType.SUSPEND ? Sanction.SEVEN_DAYS : null, adminId, source);
    }

    private String outcome(Supplier<?> work) {
        try { work.get(); return "OK"; }
        catch (BusinessException error) { return ((Enum<?>) error.getErrorCode()).name(); }
    }

    private List<String> race(Supplier<String> first, Supplier<String> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = List.of(first, second).stream().map(work -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 시작 실패");
                return work.get();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    private void at(OffsetDateTime now) { given(clock.instant()).willReturn(now.toInstant()); }

    private long liveTokens() {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id=? AND revoked_at IS NULL", Long.class, userId);
    }

    private long logCount() {
        return jdbc.queryForObject("SELECT count(*) FROM admin_action_logs WHERE target_type='USER' AND target_id=?", Long.class, userId);
    }

    private void assertStatus(UserStatus status) {
        assertThat(users.findById(userId).orElseThrow().getStatus()).isEqualTo(status);
    }

    private long insertUser() {
        return jdbc.queryForObject("INSERT INTO users(username) VALUES (?) RETURNING id", Long.class,
                "s6" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    private long insertAdmin() {
        return jdbc.queryForObject("INSERT INTO admins(username,password_hash,role,status) VALUES (?,'test-only','OPERATOR','ACTIVE') RETURNING id",
                Long.class, "s6-" + UUID.randomUUID());
    }

    private static void assertCode(ThrowingCallable work, String expected) {
        assertThatThrownBy(work).isInstanceOf(BusinessException.class)
                .extracting(error -> ((Enum<?>) ((BusinessException) error).getErrorCode()).name()).isEqualTo(expected);
    }
}
