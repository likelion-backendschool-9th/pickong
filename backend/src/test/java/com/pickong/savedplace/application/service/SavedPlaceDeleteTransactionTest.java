package com.pickong.savedplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.TestcontainersConfiguration;
import com.pickong.savedplace.application.port.in.SavedPlaceCommandUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 저장 장소 삭제와 collection 리스너(SavedPlaceDeletingListener)의 컬렉션 구성 정리가 같은 트랜잭션인지 확인합니다.
 * 테스트 트랜잭션 없이 데이터를 커밋해 두고, 삭제를 실행한 트랜잭션을 롤백했을 때 양쪽이 모두 되돌아가는지 봅니다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SavedPlaceDeleteTransactionTest {

    @Autowired
    SavedPlaceCommandUseCase commands;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    private long user;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM collection_places WHERE collection_id IN (SELECT id FROM collections WHERE user_id = ?)", user);
        jdbc.update("DELETE FROM collections WHERE user_id = ?", user);
        jdbc.update("DELETE FROM saved_places WHERE user_id = ?", user);
        jdbc.update("DELETE FROM users WHERE id = ?", user);
    }

    @Test
    @DisplayName("삭제 트랜잭션이 롤백되면 저장 장소와 컬렉션 구성 · 장소 수가 함께 그대로 남는다")
    void rollsBackTogether() {
        String username = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        user = jdbc.queryForObject("INSERT INTO users (username, email) VALUES (?, ?) RETURNING id", Long.class,
                username, username + "@example.com");
        long place = jdbc.queryForObject("""
                INSERT INTO places (source_type, name, location, category, visibility)
                VALUES ('SYSTEM', 'rollback', ST_SetSRID(ST_MakePoint(127.0, 37.5), 4326)::geography, 'CAFE', 'PUBLIC')
                RETURNING id
                """, Long.class);
        long saved = jdbc.queryForObject(
                "INSERT INTO saved_places (user_id, place_id, source_type) VALUES (?, ?, 'SEARCH') RETURNING id", Long.class, user, place);
        long collection = jdbc.queryForObject(
                "INSERT INTO collections (user_id, title, place_count) VALUES (?, 'rollback', 1) RETURNING id", Long.class, user);
        jdbc.update("INSERT INTO collection_places (collection_id, saved_place_id, place_id, sort_order) VALUES (?, ?, ?, 0)",
                collection, saved, place);

        transactions.executeWithoutResult(status -> {
            commands.delete(user, List.of(saved));
            // 같은 트랜잭션 안에서는 둘 다 지워진 상태
            assertThat(jdbc.queryForObject("SELECT count(*) FROM collection_places WHERE saved_place_id = ?", Long.class, saved)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM saved_places WHERE id = ?", Long.class, saved)).isZero();
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM saved_places WHERE id = ?", Long.class, saved)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM collection_places WHERE saved_place_id = ?", Long.class, saved))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT place_count FROM collections WHERE id = ?", Integer.class, collection)).isEqualTo(1);
    }
}
