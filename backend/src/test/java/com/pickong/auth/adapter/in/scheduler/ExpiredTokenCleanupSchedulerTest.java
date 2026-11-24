package com.pickong.auth.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 만료 토큰 정리 작업 통합 테스트. 실제 PostgreSQL에 행을 넣고 작업을 직접 실행합니다.
 * (앱 컨텍스트가 뜨는 것으로 cron 설정값 · @Scheduled 등록도 함께 확인됩니다)
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpiredTokenCleanupSchedulerTest {

    @Autowired
    ExpiredTokenCleanupScheduler scheduler;

    @Autowired
    JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void setUp() {
        clean();
        userId = jdbc.queryForObject("INSERT INTO users (username) VALUES ('seoyeon') RETURNING id", Long.class);
    }

    /** 이 테스트는 실제로 커밋하므로, 끝나면 넣은 행을 지워 같은 DB를 쓰는 다른 테스트(롤백 방식)에 남기지 않습니다. */
    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
    }

    /** 이름표(token_hash)를 붙여 토큰 행 하나를 넣습니다. 만료 · 폐기 시각은 지금 기준 SQL 식으로 받습니다. */
    private void token(String name, String expiresAt, String revokedAt) {
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, family, expires_at, revoked_at) VALUES (?, ?, 'f1', "
                + expiresAt + ", " + revokedAt + ")", userId, name);
    }

    private List<String> remaining() {
        return jdbc.queryForList("SELECT token_hash FROM refresh_tokens ORDER BY token_hash", String.class);
    }

    @Test
    @DisplayName("만료된 지 7일이 지난 행만 폐기 여부와 관계없이 지우고, 그 밖의 행은 남긴다")
    void deletesOnlyLongExpired() {
        token("a-expired-8d", "now() - interval '8 days'", "NULL");
        token("b-expired-8d-revoked", "now() - interval '8 days'", "now() - interval '20 days'");
        token("c-expired-6d-revoked", "now() - interval '6 days'", "now() - interval '19 days'");
        token("d-active-revoked", "now() + interval '3 days'", "now() - interval '1 day'");
        token("e-active", "now() + interval '10 days'", "NULL");

        scheduler.run();

        // c: 만료됐지만 보관 기간 안 / d: 아직 만료 전이라 재사용 감지에 필요 / e: 쓰는 중인 토큰
        assertThat(remaining()).containsExactly("c-expired-6d-revoked", "d-active-revoked", "e-active");
    }

    @Test
    @DisplayName("지울 행이 없으면 아무것도 바꾸지 않는다 (여러 번 돌려도 결과가 같음)")
    void idempotent() {
        token("e-active", "now() + interval '10 days'", "NULL");

        scheduler.run();
        scheduler.run();

        assertThat(remaining()).containsExactly("e-active");
    }
}
