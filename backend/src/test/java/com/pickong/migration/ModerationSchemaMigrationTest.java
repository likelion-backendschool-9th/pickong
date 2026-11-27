package com.pickong.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 이 클래스 전용 컨테이너만 초기화합니다. 애플리케이션의 datasource나 환경변수를 사용하지 않습니다. */
@Testcontainers
class ModerationSchemaMigrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T03:00:00Z");
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    private JdbcTemplate jdbc;
    private long user;
    private long other;
    private long admin;
    private long report;

    @BeforeEach
    void isolatedDatabase() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }

    @Test
    void emptyDatabaseMigratesValidatesAndReplaysWithoutCreatingAnAdminAccount() {
        // 마이그레이션이 추가될 때마다 숫자를 고치지 않도록, 저장소에 있는 마이그레이션 수와 비교합니다.
        int resolved = flyway(null).info().pending().length;
        assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(resolved);
        flyway(null).validate();
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
        for (String table : List.of("admins", "admin_action_logs", "user_sanctions")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).isZero();
        }
        assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class)).isBetween(160000, 169999);
    }

    @Test
    void upgradeFromV13PreservesEveryExistingUserReportAndContentField() {
        flyway("13").migrate();
        user = insertUser("legacy_active", "ACTIVE", NOW.plusDays(1));
        other = insertUser("legacy_suspended", "SUSPENDED", NOW.plusDays(7));
        insertUser("legacy_null", "SUSPENDED", null);
        insertUser("legacy_pending", "WITHDRAWN_PENDING", null);
        insertUser("legacy_withdrawn", "WITHDRAWN", null);
        long feed = jdbc.queryForObject("INSERT INTO feeds(user_id,content,visibility,hidden_at) VALUES (?,'원문\n둘째 줄','PRIVATE',?) RETURNING id", Long.class, user, NOW);
        long comment = jdbc.queryForObject("INSERT INTO comments(feed_id,user_id,content,hidden_at,deleted_at) VALUES (?,?,'댓글 원문',?,?) RETURNING id", Long.class, feed, other, NOW, NOW);
        jdbc.update("INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot) VALUES (?,'FEED',?,'SPAM',?)", other, feed, "{\"text\":\"원문\\n둘째 줄\",\"textScope\":\"FULL\"}");
        // 과거 처리자 999가 admins에 없어도 업그레이드는 이력과 처리 결과를 보존해야 합니다.
        jdbc.update("""
                INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot,status,resolution_action,resolution_memo,handled_by,handled_at)
                VALUES (?,'COMMENT',?,'ABUSE','legacy snapshot','RESOLVED','DISMISS','기존 기각',999,?)
                """, user, comment, NOW);
        Map<String, List<Map<String, Object>>> before = Map.of(
                "users", rows("users"), "reports", rows("reports"), "feeds", rows("feeds"), "comments", rows("comments"));
        String pendingIndex = index("ux_reports_pending_reporter_target");

        int pending = flyway(null).info().pending().length;
        assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(pending);
        flyway(null).validate();

        before.forEach((table, data) -> assertThat(rows(table)).as(table + " 전체 컬럼 보존").isEqualTo(data));
        assertThat(index("ux_reports_pending_reporter_target")).isEqualTo(pendingIndex);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admins", Long.class)).isZero();
        jdbc.update("UPDATE users SET status='BANNED',suspended_until=NULL WHERE id=?", user);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, user)).isEqualTo("BANNED");
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET status='UNKNOWN' WHERE id=?", user))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_users_status");
    }

    @Test
    void ddlRejectsInvalidTypePeriodAndUnicodeReasonEvenWhenApplicationValidationIsSkipped() {
        latestFixtures();
        List<Object[]> invalid = List.of(
                new Object[]{"SUSPEND_7", "사유", null},
                new Object[]{"SUSPEND", "사유", null},
                new Object[]{"SUSPEND", "사유", NOW},
                new Object[]{"SUSPEND", "사유", NOW.minusSeconds(1)},
                new Object[]{"WARNING", "사유", NOW.plusHours(1)},
                new Object[]{"BAN", "사유", NOW.plusHours(1)},
                new Object[]{"WARNING", "\u0085\u00a0\u2003\u3000", null},
                new Object[]{"WARNING", "😀".repeat(201), null},
                new Object[]{"WARNING", null, null});
        for (Object[] row : invalid) {
            assertThatThrownBy(() -> insertSanction(user, row[0], row[1], row[2], null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(rows("user_sanctions")).isEmpty();
        insertSanction(user, "WARNING", "😀".repeat(200), null, null);
        insertSanction(user, "WARNING", "반복 경고", null, null);
        insertSanction(user, "SUSPEND", "7일 정지", NOW.plusHours(168), null);
        insertSanction(other, "BAN", "영구 정지", null, null);
        assertThat(rows("user_sanctions")).hasSize(4);
    }

    @Test
    void overdueOpenRestrictionMustBeEndedBeforeReplacementAndKeepsItsOriginalDeadline() {
        latestFixtures();
        long id = insertSanction(user, "SUSPEND", "만료 이력", NOW.plusHours(168), null);
        jdbc.update("UPDATE user_sanctions SET created_at=?,suspended_until=? WHERE id=?", NOW.minusDays(30), NOW.minusDays(23), id);
        // DB에는 현재 시각으로 판정하는 인덱스가 없습니다. 종료 처리는 잠금을 잡은 UseCase의 책임입니다.
        assertThat(index("ux_user_sanctions_open_restriction")).contains("ended_at IS NULL").doesNotContain("now()");
        assertThatThrownBy(() -> insertSanction(user, "BAN", "전환", null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ux_user_sanctions_open_restriction");
        jdbc.update("UPDATE user_sanctions SET ended_at=suspended_until,end_reason='EXPIRED' WHERE id=?", id);
        insertSanction(user, "BAN", "새 제재", null, null);
        assertThat(jdbc.queryForObject("SELECT suspended_until FROM user_sanctions WHERE id=?", OffsetDateTime.class, id)).isEqualTo(NOW.minusDays(23));
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM user_sanctions WHERE id=?", OffsetDateTime.class, id)).isNull();
        assertThat(rows("user_sanctions")).hasSize(2);
    }

    @Test
    void manualRevokeRequiresActorAndTimeAndSourceReportStaysUniqueAfterRevoke() {
        latestFixtures();
        long id = insertSanction(user, "WARNING", "원래 사유", null, report);
        for (String update : List.of(
                "ended_at=created_at-interval '1 second',end_reason='SUPERSEDED_BY_BAN'",
                "ended_at=created_at,end_reason='EXPIRED'",
                "ended_at=created_at,end_reason='SUPERSEDED_BY_BAN'",
                "ended_at=created_at,end_reason='REVOKED'",
                "revoked_at=created_at,revoked_by_admin_id=" + admin,
                "ended_at=created_at,end_reason='REVOKED',revoked_at=created_at,revoked_by_admin_id=" + admin + ",revoke_reason=repeat('가',201)")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE user_sanctions SET " + update + " WHERE id=?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update("UPDATE user_sanctions SET ended_at=created_at,end_reason='REVOKED',revoked_at=created_at,revoked_by_admin_id=?,revoke_reason='오인 제재' WHERE id=?", admin, id);
        assertThatThrownBy(() -> insertSanction(other, "WARNING", "중복 원인", null, report))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_user_sanctions_source_report");
        assertThat(jdbc.queryForObject("SELECT reason FROM user_sanctions WHERE id=?", String.class, id)).isEqualTo("원래 사유");
        assertThat(rows("user_sanctions")).hasSize(1);
    }

    @Test
    void foreignKeysRejectOrphansAndPreventCascadeDeletionOfEvidence() {
        latestFixtures();
        assertThatThrownBy(() -> insertSanction(Long.MAX_VALUE, "WARNING", "사유", null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_user_sanctions_user");
        assertThatThrownBy(() -> insertSanction(user, "WARNING", "사유", null, Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_user_sanctions_source_report");
        long id = insertSanction(user, "WARNING", "증거 보존", null, report);
        jdbc.update("INSERT INTO admin_action_logs(admin_id,action_type,target_type,target_id,source_report_id,detail) VALUES (?,'SANCTION_USER','USER',?,?,?)",
                admin, user, report, "{\"sanctionId\":" + id + "}");
        for (String table : List.of("users", "admins", "reports")) {
            long target = table.equals("users") ? user : table.equals("admins") ? admin : report;
            assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table + " WHERE id=?", target)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(rows("user_sanctions")).hasSize(1);
        assertThat(rows("admin_action_logs")).hasSize(1);
    }

    private void latestFixtures() {
        flyway(null).migrate();
        user = insertUser("subject", "ACTIVE", null);
        other = insertUser("reporter", "ACTIVE", null);
        admin = jdbc.queryForObject("INSERT INTO admins(username,password_hash,role,status) VALUES ('test-actor','test-only','OPERATOR','ACTIVE') RETURNING id", Long.class);
        report = jdbc.queryForObject("INSERT INTO reports(reporter_id,target_type,target_id,reason,snapshot) VALUES (?,'USER',?,'SPAM','원문 보존') RETURNING id", Long.class, other, user);
    }

    private long insertUser(String name, String status, OffsetDateTime until) {
        return jdbc.queryForObject("INSERT INTO users(username,status,suspended_until) VALUES (?,?,?) RETURNING id", Long.class, name, status, until);
    }

    private long insertSanction(long subject, Object type, Object reason, Object until, Long source) {
        return jdbc.queryForObject("INSERT INTO user_sanctions(user_id,type,reason,suspended_until,admin_id,source_report_id,created_at) VALUES (?,?,?,?,?,?,?) RETURNING id",
                Long.class, subject, type, reason, until, admin, source, NOW);
    }

    private List<Map<String, Object>> rows(String table) { return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"); }
    private String index(String name) { return jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND indexname=?", String.class, name); }
    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
