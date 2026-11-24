package com.pickong.user.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.TestcontainersConfiguration;
import com.pickong.user.domain.event.UserWithdrawnEvent;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 탈퇴 회원 익명화 작업 통합 테스트. 실제 PostgreSQL에 회원 · 소셜 연결 · 토큰을 넣고 작업을 직접 실행합니다.
 * 다른 모듈의 정리(UserWithdrawnEvent 구독)가 실패하면 익명화까지 되돌아가는지는, 테스트용 구독자로 일부러 실패시켜 확인합니다.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, WithdrawalAnonymizationSchedulerTest.FailingSubscriber.class})
class WithdrawalAnonymizationSchedulerTest {

    /**
     * 정리에 실패하는 다른 모듈 흉내. {@link #failFor}에 넣은 회원의 이벤트에서만 예외를 던집니다.
     * (실제 구독자인 auth의 UserWithdrawnListener와 같은 트랜잭션에서 실행됩니다)
     */
    @TestConfiguration
    static class FailingSubscriber {
        static volatile Long failFor;

        @Bean
        Object failingWithdrawnListener() {
            return new Object() {
                @EventListener
                public void on(UserWithdrawnEvent event) {
                    if (event.userId().equals(failFor)) {
                        throw new IllegalStateException("다른 모듈의 정리 실패 (테스트)");
                    }
                }
            };
        }
    }

    @Autowired
    WithdrawalAnonymizationScheduler scheduler;

    @Autowired
    JdbcTemplate jdbc;

    private long due;
    private long due2;
    private long inGrace;
    private long active;

    @BeforeEach
    void setUp() {
        clean();
        FailingSubscriber.failFor = null;
        due = user("seoyeon", "WITHDRAWN_PENDING", "now() - interval '31 days'");
        due2 = user("jimin", "WITHDRAWN_PENDING", "now() - interval '40 days'");
        inGrace = user("haneul", "WITHDRAWN_PENDING", "now() - interval '10 days'");
        active = user("minho", "ACTIVE", "NULL");
        for (long id : new long[] {due, due2, inGrace, active}) {
            jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_id, email) VALUES (?, 'KAKAO', ?, ?)",
                    id, "kakao-" + id, "u" + id + "@kakao.com");
            jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, family, expires_at) VALUES (?, ?, 'f', now() + interval '1 day')",
                    id, "hash-" + id);
        }
    }

    /** 이 테스트는 실제로 커밋하므로, 끝나면 넣은 행을 지워 같은 DB를 쓰는 다른 테스트(롤백 방식)에 남기지 않습니다. */
    @AfterEach
    void tearDown() {
        FailingSubscriber.failFor = null;
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
    }

    private long user(String username, String status, String requestedAt) {
        return jdbc.queryForObject("""
                INSERT INTO users (username, email, display_name, bio, profile_image_url, region, status, withdrawal_requested_at, follower_count)
                VALUES (?, ?, '이름', '소개', 'https://img/1.png', '서울', ?, %s, 3) RETURNING id
                """.formatted(requestedAt), Long.class, username, username + "@kakao.com", status);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("""
                SELECT username, email, display_name, bio, profile_image_url, region, status, deleted_at, follower_count
                FROM users WHERE id = ?
                """, id);
    }

    private int count(String table, long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    @DisplayName("30일이 지난 탈퇴 회원만 익명화: 개인정보 삭제 · 아이디 deleted_{id} · WITHDRAWN, 소셜 연결 · 토큰 삭제. 유예 중 · 활성 회원은 그대로")
    void anonymizesOnlyDue() {
        scheduler.run();

        for (long id : new long[] {due, due2}) {
            Map<String, Object> row = row(id);
            assertThat(row.get("username")).isEqualTo("deleted_" + id);
            assertThat(row.get("status")).isEqualTo("WITHDRAWN");
            assertThat(row.get("deleted_at")).isNotNull();
            for (String column : new String[] {"email", "display_name", "bio", "profile_image_url", "region"}) {
                assertThat(row.get(column)).as(column).isNull();
            }
            assertThat(row.get("follower_count")).as("집계 컬럼은 건드리지 않음").isEqualTo(3L);
            assertThat(count("user_social_accounts", id)).isZero();
            assertThat(count("refresh_tokens", id)).isZero();
        }
        for (long id : new long[] {inGrace, active}) {
            assertThat(row(id).get("deleted_at")).isNull();
            assertThat(row(id).get("email")).isNotNull();
            assertThat(count("user_social_accounts", id)).isEqualTo(1);
            assertThat(count("refresh_tokens", id)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("익명화하면 원래 아이디와 같은 소셜 계정을 다른(새) 회원이 쓸 수 있다")
    void releasesUsernameAndSocialAccount() {
        scheduler.run();

        long newcomer = user("seoyeon", "ACTIVE", "NULL");
        jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_id) VALUES (?, 'KAKAO', ?)", newcomer, "kakao-" + due);
        assertThat(count("user_social_accounts", newcomer)).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 번 돌려도 결과가 같다 (이미 익명화한 회원은 다시 처리하지 않음)")
    void idempotent() {
        scheduler.run();
        Map<String, Object> first = row(due);

        scheduler.run();

        assertThat(row(due)).isEqualTo(first);
    }

    @Test
    @DisplayName("다른 모듈의 정리가 실패하면 그 회원은 익명화까지 되돌아가 탈퇴 중으로 남고(다음 날 다시), 다른 회원은 그대로 처리된다")
    void failedCleanupRollsBackOnlyThatUser() {
        FailingSubscriber.failFor = due;

        scheduler.run();

        Map<String, Object> failed = row(due);
        assertThat(failed.get("status")).isEqualTo("WITHDRAWN_PENDING");
        assertThat(failed.get("username")).isEqualTo("seoyeon");
        assertThat(count("user_social_accounts", due)).as("auth의 정리도 함께 되돌아감").isEqualTo(1);
        assertThat(count("refresh_tokens", due)).isEqualTo(1);
        assertThat(row(due2).get("status")).as("다른 회원은 처리됨").isEqualTo("WITHDRAWN");

        FailingSubscriber.failFor = null;
        scheduler.run();

        assertThat(row(due).get("status")).as("다음 실행에서 다시 처리").isEqualTo("WITHDRAWN");
        assertThat(count("user_social_accounts", due)).isZero();
    }
}
