package com.pickong.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V5(places) · V6(place_submissions) 제약 테스트.
 * 승인 행은 공개 장소와 함께 있어야 하고, 대기 · 반려 행은 장소 id가 비어 있어야 합니다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PlaceSubmissionSchemaMigrationTest {

    private static final String POINT = "ST_GeogFromText('SRID=4326;POINT(126.9254 37.5628)')";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway V5 · V6이 적용되어 장소와 제보 테이블이 생긴다")
    void migrationApplied() {
        Integer places = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '5' AND success", Integer.class);
        Integer submissions = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '6' AND success", Integer.class);
        assertThat(places).isEqualTo(1);
        assertThat(submissions).isEqualTo(1);
    }

    @Test
    @DisplayName("접수한 제보는 심사 대기이고 공개 장소와 연결되지 않는다")
    void pendingHasNoPlace() {
        long userId = insertUser("seoyeon");

        long id = insertPending(userId, "연남 골목 책방");

        var row = jdbc.queryForMap("SELECT status, place_id, reviewer_admin_id FROM place_submissions WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("place_id")).isNull();
        assertThat(row.get("reviewer_admin_id")).isNull();
    }

    @Test
    @DisplayName("같은 좌표의 제보를 두 건 저장할 수 있다")
    void duplicateCoordinatesAllowed() {
        long userId = insertUser("seoyeon");

        insertPending(userId, "책방");
        insertPending(userId, "책방 옆");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM place_submissions WHERE name IN ('책방', '책방 옆')", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("승인은 공개 장소 id와 처리 관리자가 함께 있어야 한다")
    void approveLinksPlace() {
        long userId = insertUser("seoyeon");
        long submissionId = insertPending(userId, "연남 골목 책방");
        long placeId = insertPlace("연남 골목 책방");

        jdbc.update("""
                UPDATE place_submissions
                   SET status = 'APPROVED', reviewed_at = now(), reviewer_admin_id = 7, place_id = ?
                 WHERE id = ?
                """, placeId, submissionId);

        Long linked = jdbc.queryForObject(
                "SELECT place_id FROM place_submissions WHERE id = ?", Long.class, submissionId);
        assertThat(linked).isEqualTo(placeId);
    }

    @Test
    @DisplayName("반려는 공백이 아닌 사유와 처리 관리자가 함께 있어야 하고 장소는 없다")
    void rejectStoresReason() {
        long userId = insertUser("seoyeon");
        long submissionId = insertPending(userId, "연남 골목 책방");

        jdbc.update("""
                UPDATE place_submissions
                   SET status = 'REJECTED', reviewed_at = now(), reviewer_admin_id = 7, reject_reason = '위치가 불명확합니다'
                 WHERE id = ?
                """, submissionId);

        String reason = jdbc.queryForObject(
                "SELECT reject_reason FROM place_submissions WHERE id = ?", String.class, submissionId);
        assertThat(reason).isEqualTo("위치가 불명확합니다");
    }

    @Test
    @DisplayName("장소 없이 승인하거나, 사유 없이 반려하거나, 빈 이름은 저장되지 않는다")
    void reviewStateAndNameConstraints() {
        long userId = insertUser("seoyeon");
        long submissionId = insertPending(userId, "연남 골목 책방");

        expectIntegrityViolation(() -> jdbc.update("""
                UPDATE place_submissions
                   SET status = 'APPROVED', reviewed_at = now(), reviewer_admin_id = 7
                 WHERE id = ?
                """, submissionId));

        expectIntegrityViolation(() -> jdbc.update("""
                UPDATE place_submissions
                   SET status = 'REJECTED', reviewed_at = now(), reviewer_admin_id = 7, reject_reason = '   '
                 WHERE id = ?
                """, submissionId));

        expectIntegrityViolation(() -> insertPending(userId, "   "));
    }

    @Test
    @DisplayName("설명과 반려 사유는 200자를 넘길 수 없고, 없는 회원 · 장소는 연결할 수 없다")
    void lengthAndForeignKeys() {
        long userId = insertUser("seoyeon");
        String tooLong = "가".repeat(201);

        expectIntegrityViolation(() -> jdbc.update("""
                INSERT INTO place_submissions (submitter_user_id, name, category, description, location)
                VALUES (?, '책방', 'SHOP', ?, %s)
                """.formatted(POINT), userId, tooLong));

        expectIntegrityViolation(() -> jdbc.update(
                "INSERT INTO place_submissions (submitter_user_id, name, category, location) VALUES (0, '책방', 'SHOP', " + POINT + ")"));

        long submissionId = insertPending(userId, "연남 골목 책방");
        expectIntegrityViolation(() -> jdbc.update("""
                UPDATE place_submissions
                   SET status = 'APPROVED', reviewed_at = now(), reviewer_admin_id = 7, place_id = 0
                 WHERE id = ?
                """, submissionId));
    }

    /** PostgreSQL은 제약 위반 뒤 같은 트랜잭션의 다음 명령을 거절하므로, 검사마다 저장점으로 되돌립니다. */
    private void expectIntegrityViolation(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        jdbc.execute("SAVEPOINT place_submission_constraint");
        try {
            assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("ROLLBACK TO SAVEPOINT place_submission_constraint");
        }
    }

    private long insertUser(String username) {
        return jdbc.queryForObject(
                "INSERT INTO users (username) VALUES (?) RETURNING id", Long.class, username);
    }

    private long insertPlace(String name) {
        return jdbc.queryForObject("""
                INSERT INTO places (name, location, source_type)
                VALUES (?, %s, 'CUSTOM')
                RETURNING id
                """.formatted(POINT), Long.class, name);
    }

    private long insertPending(long userId, String name) {
        return jdbc.queryForObject("""
                INSERT INTO place_submissions (submitter_user_id, name, category, location)
                VALUES (?, ?, 'SHOP', %s)
                RETURNING id
                """.formatted(POINT), Long.class, userId, name);
    }
}
