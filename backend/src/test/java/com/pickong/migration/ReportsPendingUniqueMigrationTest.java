package com.pickong.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 약관 V12까지 적용된 데이터베이스에 신고 인덱스 V13을 올립니다.
 * 중복 PENDING이 있으면 인덱스는 실패하고, 기존 행과 스냅샷은 지우거나 바꾸지 않습니다.
 * 인덱스 적용 이후의 동시 저장은 {@code ReportPersistenceAdapterIntegrationTest}가 검증합니다.
 */
class ReportsPendingUniqueMigrationTest {

    private static final DockerImageName POSTGIS = DockerImageName.parse("postgis/postgis:16-3.5")
            .asCompatibleSubstituteFor("postgres");

    private static PostgreSQLContainer postgres;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer(POSTGIS);
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void emptySchema() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    @DisplayName("신고가 없는 데이터베이스에도 V13 부분 유니크 인덱스가 생긴다")
    void emptyDatabaseGetsThePendingIndex() throws SQLException {
        migrate(null);

        assertThat(historyDescription("12")).isEqualTo("add terms v1 1");
        assertThat(historySucceeded("12")).isEqualTo(true);
        assertThat(historyDescription("13")).isEqualTo("add reports pending unique");
        assertThat(historySucceeded("13")).isEqualTo(true);
        assertThat(indexDefinition()).contains("UNIQUE").containsIgnoringCase("WHERE").contains("PENDING");
        assertThat(reportRows()).isEmpty();
    }

    @Test
    @DisplayName("기존 신고의 상태와 스냅샷은 V13 적용 뒤에도 그대로다")
    void existingReportsStayUntouched() throws SQLException {
        migrate("12");
        long reporter = insertUser();
        long reportedUser = insertUser();
        String pendingSnapshot = snapshot("대기 원문", reportedUser);
        String resolvedSnapshot = snapshot("처리된 원문", reportedUser);
        insertReport(reporter, "FEED", 11L, "SPAM", "상세", "PENDING", pendingSnapshot);
        insertResolved(reporter, "COMMENT", 12L, "ABUSE", resolvedSnapshot, "기각 메모");
        List<String> before = reportRows();

        migrate(null);

        assertThat(reportRows()).isEqualTo(before);
        assertThat(indexDefinition()).contains("PENDING");
    }

    @Test
    @DisplayName("같은 대상의 처리 완료 신고가 여러 건이어도 V13이 적용되고 새 대기 신고를 넣을 수 있다")
    void resolvedHistoryAllowsANewPendingReport() throws SQLException {
        migrate("12");
        long reporter = insertUser();
        String first = snapshot("첫 처리", reporter);
        String second = snapshot("둘째 처리", reporter);
        insertResolved(reporter, "FEED", 4L, "ETC", first, null);
        insertResolved(reporter, "FEED", 4L, "SPAM", second, "다시 기각");
        List<String> before = reportRows();

        migrate(null);
        insertReport(reporter, "FEED", 4L, "INAPPROPRIATE", null, "PENDING", snapshot("새 대기", reporter));

        assertThat(reportRows()).startsWith(before.toArray(String[]::new));
        assertThat(reportRows()).hasSize(before.size() + 1);
        assertThat(countPending(reporter, "FEED", 4L)).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 신고자의 같은 대상 대기 신고는 각자의 스냅샷을 유지한다")
    void differentReportersKeepIndependentSnapshots() throws SQLException {
        migrate("12");
        long firstReporter = insertUser();
        long secondReporter = insertUser();
        insertReport(firstReporter, "FEED", 9L, "SPAM", null, "PENDING", snapshot("첫째 신고", 100L));
        insertReport(secondReporter, "FEED", 9L, "ABUSE", "다른 상세", "PENDING", snapshot("둘째 신고", 100L));
        List<String> before = reportRows();

        migrate(null);

        assertThat(reportRows()).isEqualTo(before);
        assertThat(countPending(firstReporter, "FEED", 9L)).isEqualTo(1);
        assertThat(countPending(secondReporter, "FEED", 9L)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 대기 신고가 이미 두 건이면 V13은 실패하고 기존 행은 남는다")
    void duplicatePendingStopsTheMigration() throws SQLException {
        migrate("12");
        long reporter = insertUser();
        insertReport(reporter, "FEED", 3L, "SPAM", "하나", "PENDING", snapshot("중복 하나", 8L));
        insertReport(reporter, "FEED", 3L, "ABUSE", "둘", "PENDING", snapshot("중복 둘", 8L));
        List<String> before = reportRows();

        assertThatThrownBy(() -> migrate(null))
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("ux_reports_pending_reporter_target");

        // PostgreSQL은 실패한 마이그레이션을 롤백해서 V13 이력 행을 남기지 않습니다.
        // 다음 기동도 같은 인덱스 생성에서 멈추므로, 중복 행을 지우기 전에는 배포할 수 없습니다.
        assertThat(reportRows()).isEqualTo(before);
        assertThat(indexDefinition()).isNull();
        assertThat(historySucceeded("12")).isEqualTo(true);
        assertThat(historySucceeded("13")).isNull();
    }

    private static String snapshot(String text, long reportedUserId) {
        return "{\"text\":\"" + text + "\",\"imageUrl\":null,\"reportedUserId\":" + reportedUserId + ",\"textScope\":\"FULL\"}";
    }

    private void migrate(String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private long insertUser() throws SQLException {
        String name = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection connection = connect();
                var statement = connection.prepareStatement(
                        "INSERT INTO users (username, display_name, email) VALUES (?, ?, ?) RETURNING id")) {
            statement.setString(1, name);
            statement.setString(2, name);
            statement.setString(3, name + "@example.com");
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void insertReport(
            long reporterId, String targetType, long targetId, String reason, String detail, String status, String snapshot)
            throws SQLException {
        try (Connection connection = connect();
                var statement = connection.prepareStatement("""
                        INSERT INTO reports (reporter_id, target_type, target_id, reason, detail, status, snapshot)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
            statement.setLong(1, reporterId);
            statement.setString(2, targetType);
            statement.setLong(3, targetId);
            statement.setString(4, reason);
            statement.setString(5, detail);
            statement.setString(6, status);
            statement.setString(7, snapshot);
            statement.executeUpdate();
        }
    }

    private void insertResolved(
            long reporterId, String targetType, long targetId, String reason, String snapshot, String memo)
            throws SQLException {
        try (Connection connection = connect();
                var statement = connection.prepareStatement("""
                        INSERT INTO reports (
                            reporter_id, target_type, target_id, reason, status, snapshot,
                            resolution_action, resolution_memo, handled_by, handled_at)
                        VALUES (?, ?, ?, ?, 'RESOLVED', ?, 'DISMISS', ?, 7, now())
                        """)) {
            statement.setLong(1, reporterId);
            statement.setString(2, targetType);
            statement.setLong(3, targetId);
            statement.setString(4, reason);
            statement.setString(5, snapshot);
            statement.setString(6, memo);
            statement.executeUpdate();
        }
    }

    private List<String> reportRows() throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT reporter_id, target_type, target_id, reason, detail, status,
                               resolution_action, resolution_memo, handled_by, snapshot
                          FROM reports
                         ORDER BY id
                        """)) {
            List<String> rows = new ArrayList<>();
            while (result.next()) {
                rows.add(String.join("|",
                        Long.toString(result.getLong("reporter_id")),
                        result.getString("target_type"),
                        Long.toString(result.getLong("target_id")),
                        result.getString("reason"),
                        String.valueOf(result.getString("detail")),
                        result.getString("status"),
                        String.valueOf(result.getString("resolution_action")),
                        String.valueOf(result.getString("resolution_memo")),
                        String.valueOf(result.getObject("handled_by")),
                        result.getString("snapshot")));
            }
            return rows;
        }
    }

    private int countPending(long reporterId, String targetType, long targetId) throws SQLException {
        try (Connection connection = connect();
                var statement = connection.prepareStatement("""
                        SELECT count(*) FROM reports
                         WHERE reporter_id = ? AND target_type = ? AND target_id = ? AND status = 'PENDING'
                        """)) {
            statement.setLong(1, reporterId);
            statement.setString(2, targetType);
            statement.setLong(3, targetId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private String indexDefinition() throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_reports_pending_reporter_target'")) {
            if (!result.next()) {
                return null;
            }
            return result.getString(1);
        }
    }

    private String historyDescription(String version) throws SQLException {
        try (Connection connection = connect();
                var statement = connection.prepareStatement(
                        "SELECT description FROM flyway_schema_history WHERE version = ?")) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return result.getString(1);
            }
        }
    }

    private Boolean historySucceeded(String version) throws SQLException {
        try (Connection connection = connect();
                var statement = connection.prepareStatement(
                        "SELECT success FROM flyway_schema_history WHERE version = ?")) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return result.getBoolean(1);
            }
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
