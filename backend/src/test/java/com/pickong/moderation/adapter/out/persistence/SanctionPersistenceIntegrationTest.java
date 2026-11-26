package com.pickong.moderation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.TestcontainersConfiguration;
import com.pickong.admin.application.dto.AdminActionLogEntry;
import com.pickong.admin.application.port.out.AdminActionLogPersistencePort;
import com.pickong.common.error.BusinessException;
import com.pickong.moderation.application.port.out.SanctionPersistencePort;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.model.Sanction;
import com.pickong.moderation.domain.vo.SanctionEndReason;
import com.pickong.moderation.domain.vo.SanctionType;
import com.pickong.user.application.port.out.UserPersistencePort;
import com.pickong.user.domain.model.User;
import com.pickong.user.domain.vo.UserStatus;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 소유 모듈의 저장 Port를 실제 PostGIS/Flyway DB에서 함께 검증합니다. 업무 UseCase는 만들지 않습니다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SanctionPersistenceIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    private static final String SNAPSHOT = "{\"text\":\"신고 당시 원문\",\"textScope\":\"FULL\"}";

    @Autowired SanctionPersistencePort sanctions;
    @Autowired AdminActionLogPersistencePort logs;
    @Autowired UserPersistencePort users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private long userId;
    private long otherUserId;
    private long adminId;
    private long reviewerId;
    private long reportId;

    @BeforeEach
    void fixtures() {
        userId = insertUser();
        otherUserId = insertUser();
        adminId = insertAdmin();
        reviewerId = insertAdmin();
        reportId = jdbc.queryForObject("""
                INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot)
                VALUES (?,'USER',?,'SPAM',?) RETURNING id
                """, Long.class, otherUserId, userId, SNAPSHOT);
    }

    @AfterEach
    void removeOnlyOwnFixtures() {
        jdbc.update("DELETE FROM admin_action_logs WHERE admin_id IN (?,?)", adminId, reviewerId);
        jdbc.update("DELETE FROM user_sanctions WHERE user_id IN (?,?)", userId, otherUserId);
        jdbc.update("DELETE FROM reports WHERE id = ?", reportId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", userId, otherUserId);
        jdbc.update("DELETE FROM admins WHERE id IN (?,?)", adminId, reviewerId);
    }

    @ParameterizedTest
    @EnumSource(SanctionType.class)
    void insertAndReadAllTypes(SanctionType type) {
        Sanction saved = tx(() -> sanctions.save(newSanction(userId, type, reportId, NOW)));
        assertThat(saved.getId()).isPositive();
        assertThat(sanctions.findById(saved.getId())).hasValueSatisfying(value ->
                assertThat(value).usingRecursiveComparison().isEqualTo(saved));
        assertThat(sanctions.findBySourceReportId(reportId)).hasValueSatisfying(value ->
                assertThat(value.getId()).isEqualTo(saved.getId()));
        assertThat(sanctions.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(sanctions.findBySourceReportId(Long.MAX_VALUE)).isEmpty();
        assertThat(sanctions.findByUserId(otherUserId)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(SanctionEndReason.class)
    void endingRoundTripAndReplay(SanctionEndReason reason) {
        Sanction saved = tx(() -> sanctions.save(newSanction(userId, SanctionType.SUSPEND, reportId, NOW)));
        Sanction ended = switch (reason) {
            case EXPIRED -> saved.expireIfDue(saved.getSuspendedUntil().plusHours(5));
            case REVOKED -> saved.revoke(reviewerId, "오처리 정정", NOW.plusHours(1));
            case SUPERSEDED_BY_BAN -> saved.supersedeByBan(NOW.plusHours(1));
        };
        assertThat(tx(() -> sanctions.endIfOpen(ended))).isTrue();
        assertThat(sanctions.findById(saved.getId())).hasValueSatisfying(value ->
                assertThat(value).usingRecursiveComparison().isEqualTo(ended));
        assertThat(sanctions.findOpenByUserId(userId)).isEmpty();
        assertThat(sanctions.findByUserId(userId)).hasSize(1);
        assertThat(tx(() -> sanctions.endIfOpen(ended))).isFalse();
        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE id=?", String.class, reportId)).isEqualTo(SNAPSHOT);
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id=?", String.class, reportId)).isEqualTo("PENDING");
    }

    @Test
    void endingCannotOverwriteOriginalEvidenceOrAnotherUsersHistory() {
        Sanction saved = tx(() -> sanctions.save(newSanction(userId, SanctionType.SUSPEND, reportId, NOW)));
        Sanction ended = saved.revoke(reviewerId, null, NOW.plusHours(1));
        assertThat(tx(() -> sanctions.endIfOpen(ended.toBuilder().userId(otherUserId).build()))).isFalse();
        assertThat(tx(() -> sanctions.endIfOpen(ended.toBuilder().id(Long.MAX_VALUE).build()))).isFalse();
        assertThat(tx(() -> sanctions.endIfOpen(ended.toBuilder().reason("덮어쓰기 시도").sourceReportId(null).build()))).isTrue();
        Sanction loaded = sanctions.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getReason()).isEqualTo(saved.getReason());
        assertThat(loaded.getSourceReportId()).isEqualTo(reportId);
        assertThat(loaded.getSuspendedUntil()).isEqualTo(saved.getSuspendedUntil());
        assertThat(loaded.getAdminId()).isEqualTo(adminId);
    }

    @Test
    void insertIsNotMergeAndEndNeedsAnEnding() {
        Sanction saved = tx(() -> sanctions.save(newSanction(userId, SanctionType.WARNING, null, NOW)));
        assertThatThrownBy(() -> tx(() -> sanctions.save(saved))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx(() -> sanctions.endIfOpen(saved))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx(() -> sanctions.save(newSanction(userId, SanctionType.BAN, null, NOW)
                .toBuilder().revokedByAdminId(reviewerId).build()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historyOrderIncludesEndedAndOpenQueryIncludesOverdueRows() {
        Sanction first = tx(() -> sanctions.save(newSanction(userId, SanctionType.WARNING, null, NOW)));
        Sanction second = tx(() -> sanctions.save(newSanction(userId, SanctionType.WARNING, null, NOW)));
        Sanction overdue = tx(() -> sanctions.save(newSanction(userId, SanctionType.SUSPEND, null, NOW.minusDays(30))));
        assertThat(sanctions.findByUserId(userId)).extracting(Sanction::getId)
                .containsExactly(second.getId(), first.getId(), overdue.getId());
        assertThat(sanctions.findOpenByUserId(userId)).extracting(Sanction::getId)
                .containsExactly(overdue.getId(), first.getId(), second.getId());
        assertThat(overdue.isEffectiveAt(NOW)).isFalse();
        tx(() -> {
            assertThat(sanctions.endIfOpen(overdue.expireIfDue(NOW))).isTrue();
            return sanctions.save(newSanction(userId, SanctionType.BAN, null, NOW.plusHours(1)));
        });
        assertThat(sanctions.findByUserId(userId)).hasSize(4);
        assertThat(sanctions.findOpenByUserId(userId)).hasSize(3);
    }

    @Test
    void uniqueRestrictionsAndSourceHaveSpecificErrors() {
        Sanction existing = tx(() -> sanctions.save(newSanction(userId, SanctionType.BAN, null, NOW)));
        assertBusiness(() -> tx(() -> sanctions.save(newSanction(userId, SanctionType.SUSPEND, null, NOW))),
                ModerationErrorCode.ACTIVE_SANCTION_EXISTS);
        tx(() -> sanctions.save(newSanction(userId, SanctionType.WARNING, reportId, NOW)));
        assertBusiness(() -> tx(() -> sanctions.save(newSanction(otherUserId, SanctionType.WARNING, reportId, NOW))),
                ModerationErrorCode.DUPLICATE_SANCTION_SOURCE);
        tx(() -> sanctions.endIfOpen(existing.revoke(reviewerId, null, NOW.plusHours(1))));
        tx(() -> sanctions.save(newSanction(userId, SanctionType.SUSPEND, null, NOW.plusHours(1))));
    }

    @Test
    void sourceRemainsUniqueAfterEnding() {
        Sanction saved = tx(() -> sanctions.save(newSanction(userId, SanctionType.SUSPEND, reportId, NOW)));
        tx(() -> sanctions.endIfOpen(saved.revoke(reviewerId, null, NOW.plusHours(1))));
        assertBusiness(() -> tx(() -> sanctions.save(newSanction(userId, SanctionType.BAN, reportId, NOW.plusHours(1)))),
                ModerationErrorCode.DUPLICATE_SANCTION_SOURCE);
    }

    @Test
    void foreignKeysAndChecksAreNotTranslatedToDuplicateErrors() {
        assertThatThrownBy(() -> tx(() -> sanctions.save(newSanction(userId, SanctionType.WARNING, null, NOW)
                .toBuilder().adminId(Long.MAX_VALUE).build()))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tx(() -> sanctions.save(newSanction(userId, SanctionType.WARNING, Long.MAX_VALUE, NOW))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tx(() -> sanctions.save(newSanction(userId, SanctionType.WARNING, null, NOW)
                .toBuilder().reason(" ").build()))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sanctions.findByUserId(userId)).isEmpty();
    }

    @Test
    void userStatusUpdatesPreserveProfileCountersAndReadFreshValues() {
        tx(() -> {
            User previous = users.findByIdForUpdate(userId).orElseThrow();
            jdbc.update("UPDATE users SET bio='동시 프로필', feed_count=17, follower_count=23 WHERE id=?", userId);
            assertThat(users.updateStatusIfUnchanged(previous, previous.ban(NOW))).isTrue();
            User fresh = users.findById(userId).orElseThrow();
            assertThat(fresh.getStatus()).isEqualTo(UserStatus.BANNED);
            assertThat(fresh.getSuspendedUntil()).isNull();
            assertThat(fresh.getBio()).isEqualTo("동시 프로필");
            assertThat(fresh.getFeedCount()).isEqualTo(17L);
            assertThat(fresh.getFollowerCount()).isEqualTo(23L);
            assertThat(fresh.getUsername()).isEqualTo(previous.getUsername());
            assertThat(fresh.getWithdrawalRequestedAt()).isNull();
            assertThat(users.updateStatusIfUnchanged(previous, previous.suspend(NOW.plusHours(168), NOW))).isFalse();
            return null;
        });
    }

    @Test
    void compareAndSetChecksDeadlineAndWithdrawal() {
        User initial = users.findById(userId).orElseThrow();
        User suspended = initial.suspend(NOW.plusHours(168), NOW);
        tx(() -> users.updateStatusIfUnchanged(initial, suspended));
        jdbc.update("UPDATE users SET suspended_until=? WHERE id=?", NOW.plusHours(720), userId);
        assertThat(tx(() -> users.updateStatusIfUnchanged(suspended, suspended.ban(NOW.plusHours(1))))).isFalse();
        jdbc.update("UPDATE users SET status='WITHDRAWN_PENDING', withdrawal_requested_at=? WHERE id=?", NOW, userId);
        assertThat(tx(() -> users.updateStatusIfUnchanged(suspended, suspended.ban(NOW.plusHours(1))))).isFalse();
        User withdrawn = users.findById(userId).orElseThrow();
        assertThatThrownBy(() -> tx(() -> users.updateStatusIfUnchanged(withdrawn, initial)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tx(() -> users.findByIdForUpdate(Long.MAX_VALUE))).isEmpty();
        assertThat(tx(() -> users.updateStatusIfUnchanged(initial.toBuilder().id(Long.MAX_VALUE).build(),
                initial.toBuilder().id(Long.MAX_VALUE).build().ban(NOW)))).isFalse();
    }

    @Test
    void clearingLastRestrictionClearsOnlyAccountStatusFields() {
        tx(() -> {
            User before = users.findByIdForUpdate(userId).orElseThrow();
            User suspended = before.suspend(NOW.plusHours(168), NOW);
            assertThat(users.updateStatusIfUnchanged(before, suspended)).isTrue();
            User loaded = users.findById(userId).orElseThrow();
            assertThat(loaded.getSuspendedUntil()).isEqualTo(NOW.plusHours(168));
            assertThat(users.updateStatusIfUnchanged(loaded, loaded.lift(false, NOW.plusHours(1)))).isTrue();
            assertThat(users.findById(userId)).hasValueSatisfying(value -> {
                assertThat(value.getStatus()).isEqualTo(UserStatus.ACTIVE);
                assertThat(value.getSuspendedUntil()).isNull();
                assertThat(value.getBio()).isEqualTo(before.getBio());
            });
            return null;
        });
    }

    @Test
    void lockRefreshesAnAlreadyManagedUser() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            tx(() -> {
                assertThat(users.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
                try {
                    executor.submit(() -> jdbc.update("UPDATE users SET status='BANNED' WHERE id=?", userId))
                            .get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                assertThat(users.findByIdForUpdate(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.BANNED);
                return null;
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rowLockIsHeldUntilCallerTransactionEnds() {
        var executor = Executors.newSingleThreadExecutor();
        try {
            tx(() -> {
                users.findByIdForUpdate(userId).orElseThrow();
                var competitor = executor.submit(() -> {
                    try {
                        return tx(() -> {
                            jdbc.execute("SET LOCAL lock_timeout='300ms'");
                            users.findByIdForUpdate(userId);
                            return "unexpected lock acquisition";
                        });
                    } catch (RuntimeException exception) {
                        Throwable root = exception;
                        while (root.getCause() != null) root = root.getCause();
                        if (root instanceof SQLException sql) return sql.getSQLState();
                        throw exception;
                    }
                });
                try {
                    assertThat(competitor.get(10, TimeUnit.SECONDS)).isEqualTo("55P03");
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                return null;
            });
            assertThat(tx(() -> users.findByIdForUpdate(userId))).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void writesAndLocksRequireCallerTransaction() {
        Sanction pending = newSanction(userId, SanctionType.WARNING, null, NOW);
        assertThatThrownBy(() -> sanctions.save(pending)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> sanctions.endIfOpen(pending)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> logs.append(log(adminId, null))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> users.findByIdForUpdate(userId)).isInstanceOf(IllegalTransactionStateException.class);
        User before = users.findById(userId).orElseThrow();
        assertThatThrownBy(() -> users.updateStatusIfUnchanged(before, before.ban(NOW)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void logsKeepTextReasonAndOptionalReport() {
        long direct = tx(() -> logs.append(log(adminId, null)));
        long reported = tx(() -> logs.append(log(reviewerId, reportId)));
        var row = jdbc.queryForMap("SELECT * FROM admin_action_logs WHERE id=?", reported);
        assertThat(direct).isPositive();
        assertThat(reported).isGreaterThan(direct);
        assertThat(row.get("admin_id")).isEqualTo(reviewerId);
        assertThat(row.get("action_type")).isEqualTo("SANCTION_USER");
        assertThat(row.get("target_type")).isEqualTo("USER");
        assertThat(row.get("target_id")).isEqualTo(userId);
        assertThat(row.get("reason")).isEqualTo("처리 사유");
        assertThat(row.get("detail")).isEqualTo("JSON 강제 없이\n상세 원문 😀");
        assertThat(row.get("source_report_id")).isEqualTo(reportId);
        assertThat(jdbc.queryForObject("SELECT source_report_id FROM admin_action_logs WHERE id=?", Long.class, direct)).isNull();
        assertThat(jdbc.queryForObject("SELECT created_at FROM admin_action_logs WHERE id=?", OffsetDateTime.class, reported)).isEqualTo(NOW);
    }

    @Test
    void logFailureRollsBackAccountSanctionAndSelectedReport() {
        assertThatThrownBy(() -> tx(() -> {
            User previous = users.findByIdForUpdate(userId).orElseThrow();
            assertThat(users.updateStatusIfUnchanged(previous, previous.ban(NOW))).isTrue();
            sanctions.save(newSanction(userId, SanctionType.BAN, reportId, NOW));
            jdbc.update("""
                    UPDATE reports SET status='RESOLVED',resolution_action='SANCTION_USER',resolution_memo='사유',
                    handled_by=?,handled_at=? WHERE id=?
                    """, adminId, NOW, reportId);
            return logs.append(log(Long.MAX_VALUE, reportId));
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctions.findByUserId(userId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id=?", String.class, reportId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE id=?", String.class, reportId)).isEqualTo(SNAPSHOT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_action_logs WHERE admin_id=?", Long.class, adminId)).isZero();
    }

    @Test
    void successfulWritesShareCallerTransaction() {
        tx(() -> {
            User previous = users.findByIdForUpdate(userId).orElseThrow();
            users.updateStatusIfUnchanged(previous, previous.ban(NOW));
            sanctions.save(newSanction(userId, SanctionType.BAN, reportId, NOW));
            return logs.append(log(adminId, reportId));
        });
        assertThat(users.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(sanctions.findBySourceReportId(reportId)).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_action_logs WHERE admin_id=?", Long.class, adminId)).isEqualTo(1);
    }

    @Test
    void concurrentRestrictionsAndSameReportHaveOneWinner() throws Exception {
        List<String> restrictionResults = race(
                () -> saveOutcome(newSanction(userId, SanctionType.BAN, null, NOW)),
                () -> saveOutcome(newSanction(userId, SanctionType.SUSPEND, null, NOW)));
        assertThat(restrictionResults).containsExactlyInAnyOrder("SAVED", "ACTIVE_SANCTION_EXISTS");
        List<String> sourceResults = race(
                () -> saveOutcome(newSanction(userId, SanctionType.WARNING, reportId, NOW)),
                () -> saveOutcome(newSanction(otherUserId, SanctionType.WARNING, reportId, NOW)));
        assertThat(sourceResults).containsExactlyInAnyOrder("SAVED", "DUPLICATE_SANCTION_SOURCE");
    }

    @Test
    void concurrentEndingChangesHistoryOnlyOnce() throws Exception {
        Sanction saved = tx(() -> sanctions.save(newSanction(userId, SanctionType.SUSPEND, null, NOW)));
        List<Boolean> results = race(
                () -> tx(() -> sanctions.endIfOpen(saved.revoke(adminId, "첫 관리자", NOW.plusHours(1)))),
                () -> tx(() -> sanctions.endIfOpen(saved.revoke(reviewerId, "다른 관리자", NOW.plusHours(1)))));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        Sanction loaded = sanctions.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getRevokedByAdminId()).isIn(adminId, reviewerId);
        assertThat(loaded.getReason()).isEqualTo(saved.getReason());
        assertThat(sanctions.findByUserId(userId)).hasSize(1);
    }

    private String saveOutcome(Sanction sanction) {
        try {
            tx(() -> sanctions.save(sanction));
            return "SAVED";
        } catch (BusinessException exception) {
            return ((ModerationErrorCode) exception.getErrorCode()).name();
        }
    }

    private static void assertBusiness(Supplier<?> action, ModerationErrorCode expected) {
        assertThatThrownBy(action::get).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode()).isEqualTo(expected);
    }

    private <T> List<T> race(Supplier<T> first, Supplier<T> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = List.of(first, second).stream().map(action -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 시작 실패");
                return action.get();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Sanction newSanction(long targetUser, SanctionType type, Long source, OffsetDateTime at) {
        return Sanction.create(targetUser, type, "원래 사유 😀", type == SanctionType.SUSPEND ? Sanction.SEVEN_DAYS : null,
                adminId, source, at);
    }

    private AdminActionLogEntry log(long actor, Long source) {
        return new AdminActionLogEntry(actor, "SANCTION_USER", "USER", userId,
                "처리 사유", "JSON 강제 없이\n상세 원문 😀", source, NOW);
    }

    private <T> T tx(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private long insertUser() {
        return jdbc.queryForObject("INSERT INTO users(username,bio,feed_count) VALUES (?,'기존 소개',3) RETURNING id",
                Long.class, "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    private long insertAdmin() {
        return jdbc.queryForObject("""
                INSERT INTO admins(username,password_hash,role,status)
                VALUES (?,'test-only-hash','OPERATOR','ACTIVE') RETURNING id
                """, Long.class, "persistence-" + UUID.randomUUID());
    }
}
