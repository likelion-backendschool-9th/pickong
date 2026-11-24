package com.pickong.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.application.port.in.TokenUseCase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** GET · PATCH /me 통합 테스트. 실제 PostgreSQL(V3 컬럼 포함) · 보안 필터 · 쿠키를 그대로 거칩니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenUseCase tokenUseCase;

    private long me;
    private Cookie access;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
        me = jdbc.queryForObject("""
                INSERT INTO users (username, display_name, bio, follower_count, following_count, feed_count)
                VALUES ('seoyeon', '서연', '카페 투어', 12, 3, 7) RETURNING id
                """, Long.class);
        access = new Cookie(AuthCookies.ACCESS_TOKEN, tokenUseCase.issue(me).accessToken());
    }

    private ResultActions patchMe(String json) throws Exception {
        return mvc.perform(patch("/api/v1/me").servletPath("/api/v1").with(csrf()).cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT username, display_name, bio, region, username_changed_at, follower_count FROM users WHERE id = ?", me);
    }

    // ── GET ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /me: 프론트 UserDto 모양, id는 public_id, 집계 컬럼 그대로, 아이디는 지금 바꿀 수 있음(null)")
    void getMe() throws Exception {
        String publicId = jdbc.queryForObject("SELECT CAST(public_id AS TEXT) FROM users WHERE id = ?", String.class, me);

        mvc.perform(get("/api/v1/me").servletPath("/api/v1").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicId))
                .andExpect(jsonPath("$.username").value("seoyeon"))
                .andExpect(jsonPath("$.nickname").value("서연"))
                .andExpect(jsonPath("$.name").value("서연"))
                .andExpect(jsonPath("$.bio").value("카페 투어"))
                .andExpect(jsonPath("$.region").doesNotExist())
                .andExpect(jsonPath("$.postCount").value(7))
                .andExpect(jsonPath("$.followerCount").value(12))
                .andExpect(jsonPath("$.followingCount").value(3))
                .andExpect(jsonPath("$.isMe").value(true))
                .andExpect(jsonPath("$.isFollowing").value(false))
                .andExpect(jsonPath("$.isCreator").value(false))
                .andExpect(jsonPath("$.usernameChangeableAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /me: 로그인하지 않으면 401")
    void getMeRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/me").servletPath("/api/v1")).andExpect(status().isUnauthorized());
    }

    // ── PATCH: 보낸 항목만 ─────────────────────────────────────

    @Test
    @DisplayName("PATCH: 보낸 항목만 바뀌고 나머지는 그대로")
    void patchOnlySentFields() throws Exception {
        patchMe("{\"region\":\" 서울 성수 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("서울 성수"))
                .andExpect(jsonPath("$.name").value("서연"))
                .andExpect(jsonPath("$.bio").value("카페 투어"));

        assertThat(row()).containsEntry("username", "seoyeon").containsEntry("display_name", "서연")
                .containsEntry("region", "서울 성수");
        assertThat(row().get("username_changed_at")).isNull();
    }

    @Test
    @DisplayName("PATCH: name을 null로 보내면 이름이 지워지고 화면 이름(nickname)은 아이디가 된다")
    void patchClearsName() throws Exception {
        patchMe("{\"name\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.nickname").value("seoyeon"));

        assertThat(row().get("display_name")).isNull();
    }

    @Test
    @DisplayName("PATCH: 프로필 편집처럼 지금 아이디를 함께 보내도 아이디 변경으로 치지 않는다")
    void patchWithCurrentUsername() throws Exception {
        patchMe("{\"username\":\"seoyeon\",\"name\":\"콩이\",\"bio\":\"\",\"region\":\"서울\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usernameChangeableAt").doesNotExist());

        assertThat(row()).containsEntry("display_name", "콩이").containsEntry("region", "서울");
        assertThat(row().get("bio")).as("빈 소개는 null").isNull();
        assertThat(row().get("username_changed_at")).isNull();
    }

    @Test
    @DisplayName("PATCH: 팔로워 수 같은 집계 컬럼은 건드리지 않는다 (다른 모듈이 그 사이 바꾼 값이 유지됨)")
    void patchKeepsCounters() throws Exception {
        jdbc.update("UPDATE users SET follower_count = 99 WHERE id = ?", me);

        patchMe("{\"bio\":\"새 소개\"}").andExpect(status().isOk());

        assertThat(row()).containsEntry("follower_count", 99L);
    }

    // ── PATCH: 아이디 ──────────────────────────────────────

    @Test
    @DisplayName("아이디 변경: 소문자로 저장, 다음 변경 가능 시각(14일 뒤)을 알려 주고, 14일 안에 또 바꾸면 409 USERNAME_CHANGE_TOO_SOON")
    void changeUsername() throws Exception {
        patchMe("{\"username\":\"KongKong\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kongkong"))
                .andExpect(jsonPath("$.usernameChangeableAt").exists());
        assertThat(row().get("username_changed_at")).isNotNull();

        patchMe("{\"username\":\"kong2\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_CHANGE_TOO_SOON"));
        assertThat(row()).containsEntry("username", "kongkong");
    }

    @Test
    @DisplayName("아이디 변경: 사용 중(대소문자 무시) · 예약어면 409 USERNAME_TAKEN, 형식 오류 · null이면 400 INVALID_USERNAME")
    void changeUsernameRejected() throws Exception {
        jdbc.update("INSERT INTO users (username) VALUES ('jimin')");

        patchMe("{\"username\":\"JIMIN\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        patchMe("{\"username\":\"admin\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        patchMe("{\"username\":\"서연\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_USERNAME"));
        patchMe("{\"username\":null}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_USERNAME"));
        assertThat(row().get("username_changed_at")).as("실패한 시도는 14일 제한을 시작하지 않음").isNull();
    }

    // ── PATCH: 검증 · 보안 ──────────────────────────────────

    @Test
    @DisplayName("한 항목이라도 틀리면 아무것도 저장하지 않는다 (아이디는 맞고 소개가 151자)")
    void allOrNothing() throws Exception {
        patchMe("{\"username\":\"kong\",\"bio\":\"" + "a".repeat(151) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BIO"));

        assertThat(row()).containsEntry("username", "seoyeon").containsEntry("bio", "카페 투어");
    }

    @Test
    @DisplayName("이름 21자 · 활동 지역 21자는 400")
    void lengthLimits() throws Exception {
        patchMe("{\"name\":\"" + "가".repeat(21) + "\"}").andExpect(jsonPath("$.code").value("INVALID_NAME"));
        patchMe("{\"region\":\"" + "가".repeat(21) + "\"}").andExpect(jsonPath("$.code").value("INVALID_REGION"));
    }

    @Test
    @DisplayName("PATCH: CSRF 헤더가 없으면 403, 로그인하지 않으면 401")
    void patchSecurity() throws Exception {
        mvc.perform(patch("/api/v1/me").servletPath("/api/v1").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/me").servletPath("/api/v1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
