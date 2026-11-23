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
 * V1(인증 테이블) 마이그레이션의 제약 조건 테스트.
 * 1인 1계정 · 소셜 계정 중복 연결 금지 같은 규칙은 서비스 코드뿐 아니라 DB도 지켜야
 * 동시 요청에서도 깨지지 않으므로, 실제 PostgreSQL에서 제약이 걸리는지 확인합니다.
 * 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백됩니다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthSchemaMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private long insertUser(String username, String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email) VALUES (?, ?) RETURNING id", Long.class, username, email);
    }

    private void insertSocialAccount(long userId, String provider, String providerId, String email) {
        jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_id, email) VALUES (?, ?, ?, ?)",
                userId, provider, providerId, email);
    }

    @Test
    @DisplayName("Flyway V1이 적용되어 인증 테이블이 모두 만들어진다")
    void migrationApplied() {
        Integer version = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class);
        assertThat(version).isEqualTo(1);

        for (String table : new String[] {"users", "user_social_accounts", "refresh_tokens", "terms", "user_terms_agreements"}) {
            Integer exists = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = ?", Integer.class, table);
            assertThat(exists).as(table).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("회원 기본값: public_id는 자동 생성, 상태는 ACTIVE, 이름(display_name)은 비워 둘 수 있다")
    void userDefaults() {
        long id = insertUser("seoyeon", null);

        var row = jdbc.queryForMap("SELECT public_id, status, display_name FROM users WHERE id = ?", id);
        assertThat(row.get("public_id")).isNotNull();
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("display_name")).isNull();
    }

    @Test
    @DisplayName("닉네임(username)은 대소문자를 무시하고 중복될 수 없다")
    void usernameUniqueIgnoringCase() {
        insertUser("seoyeon", null);

        assertThatThrownBy(() -> insertUser("SeoYeon", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("가입 이메일은 대소문자를 무시하고 중복될 수 없고, 이메일이 없는 회원은 여럿일 수 있다")
    void emailUniqueIgnoringCase() {
        insertUser("seoyeon", "seoyeon@gmail.com");
        insertUser("jimin", null);
        insertUser("minsu", null);

        assertThatThrownBy(() -> insertUser("haeun", "SEOYEON@gmail.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 소셜 계정(provider, provider_id)은 다른 찜콩 계정에 연결할 수 없다")
    void socialAccountLinkedOnce() {
        long seoyeon = insertUser("seoyeon", null);
        long jimin = insertUser("jimin", null);
        insertSocialAccount(seoyeon, "KAKAO", "12345", null);

        assertThatThrownBy(() -> insertSocialAccount(jimin, "KAKAO", "12345", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("한 찜콩 계정에는 제공자마다 하나만 연결할 수 있다")
    void oneAccountPerProvider() {
        long seoyeon = insertUser("seoyeon", null);
        insertSocialAccount(seoyeon, "GOOGLE", "google-a", null);

        assertThatThrownBy(() -> insertSocialAccount(seoyeon, "GOOGLE", "google-b", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 회원의 카카오 · Google 계정은 이메일이 같아도 함께 연결할 수 있다")
    void sameEmailAcrossOwnProviders() {
        long seoyeon = insertUser("seoyeon", "seoyeon@gmail.com");
        insertSocialAccount(seoyeon, "KAKAO", "12345", "seoyeon@gmail.com");
        insertSocialAccount(seoyeon, "GOOGLE", "google-a", "seoyeon@gmail.com");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM user_social_accounts WHERE user_id = ?", Integer.class, seoyeon);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("지원하지 않는 제공자는 저장할 수 없다")
    void unknownProviderRejected() {
        long seoyeon = insertUser("seoyeon", null);

        assertThatThrownBy(() -> insertSocialAccount(seoyeon, "NAVER", "n-1", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("리프레시 토큰 해시는 중복될 수 없다")
    void refreshTokenHashUnique() {
        long seoyeon = insertUser("seoyeon", null);
        String insert = "INSERT INTO refresh_tokens (user_id, token_hash, family, expires_at) VALUES (?, ?, ?, now() + interval '14 days')";
        jdbc.update(insert, seoyeon, "hash-1", "family-1");

        assertThatThrownBy(() -> jdbc.update(insert, seoyeon, "hash-1", "family-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
