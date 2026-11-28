package com.pickong.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V22(saved_places를 ERD에 맞춤)가 V17 형식의 기존 행을 의미를 유지한 채 옮기는지 확인합니다.
 * V21까지 적용한 DB에 이전 값으로 행을 넣고 V22를 적용합니다. 이 클래스 전용 컨테이너만 씁니다.
 */
@Testcontainers
class SavedPlacesErdMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyDatabase() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }

    @Test
    @DisplayName("이전 출처 값은 ERD 값으로 옮기고 FEED와 원본 게시물 연결은 그대로 둔다. NOT_INTERESTED는 가고 싶은 곳, 긴 메모는 500자")
    void convertsV17Rows() {
        flyway("21").migrate();
        long user = jdbc.queryForObject("INSERT INTO users (username, email) VALUES ('m1', 'm1@example.com') RETURNING id", Long.class);
        long feed = jdbc.queryForObject("INSERT INTO feeds (user_id, content) VALUES (?, 'f') RETURNING id", Long.class, user);
        long fromFeed = insert(user, "FEED", feed, "WANT_TO_GO", null);
        long fromSnapshot = insert(user, "COLLECTION_SNAPSHOT", null, "VISITED", "메모");
        long fromAi = insert(user, "AI_IMPORT", null, "WANT_TO_GO", null);
        long direct = insert(user, null, null, "NOT_INTERESTED", "가".repeat(600));
        long unknown = insert(user, "SOMETHING_ELSE", null, "WANT_TO_GO", null);

        assertThat(flyway(null).migrate().migrationsExecuted).isPositive();

        assertThat(row(fromFeed)).containsEntry("source_type", "FEED").containsEntry("source_feed_id", feed);
        assertThat(row(fromSnapshot)).containsEntry("source_type", "COLLECTION").containsEntry("visit_status", "VISITED")
                .containsEntry("memo", "메모");
        assertThat(row(fromAi)).containsEntry("source_type", "LINK_IMPORT");
        Map<String, Object> notInterested = row(direct);
        assertThat(notInterested).containsEntry("source_type", "SEARCH").containsEntry("visit_status", "WANT_TO_GO");
        assertThat((String) notInterested.get("memo")).hasSize(500);
        assertThat(notInterested.get("visited_at")).isNull();
        assertThat(row(unknown)).containsEntry("source_type", "SEARCH");
        flyway(null).validate();
    }

    private long insert(long user, String source, Long feed, String visitStatus, String memo) {
        long place = jdbc.queryForObject("""
                INSERT INTO places (source_type, name, location, category, visibility)
                VALUES ('SYSTEM', 'p', ST_SetSRID(ST_MakePoint(127.0, 37.5), 4326)::geography, 'CAFE', 'PUBLIC')
                RETURNING id
                """, Long.class);
        return jdbc.queryForObject("""
                INSERT INTO saved_places (user_id, place_id, source, source_feed_id, visit_status, visited_at, memo)
                VALUES (?, ?, ?, ?, ?, CASE WHEN ? = 'VISITED' THEN now() END, ?)
                RETURNING id
                """, Long.class, user, place, source, feed, visitStatus, visitStatus, memo);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap(
                "SELECT source_type, source_feed_id, visit_status, visited_at, memo FROM saved_places WHERE id = ?", id);
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
