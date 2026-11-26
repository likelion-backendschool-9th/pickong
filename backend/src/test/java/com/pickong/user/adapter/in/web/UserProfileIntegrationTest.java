package com.pickong.user.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.application.port.in.TokenUseCase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** GET /users/{username} 통합 테스트. 실제 PostgreSQL · 보안 필터 · 쿠키를 그대로 거칩니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserProfileIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenUseCase tokenUseCase;

    private long me;
    private long other;
    private Cookie access;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
        me = jdbc.queryForObject("""
                INSERT INTO users (username, display_name, bio, region, follower_count, following_count, feed_count)
                VALUES ('seoyeon', '서연', '카페 투어', '서울', 12, 3, 7) RETURNING id
                """, Long.class);
        other = jdbc.queryForObject("INSERT INTO users (username) VALUES ('minjun') RETURNING id", Long.class);
        access = new Cookie(AuthCookies.ACCESS_TOKEN, tokenUseCase.issue(me).accessToken());
    }

    private ResultActions getProfile(String username) throws Exception {
        return mvc.perform(get("/api/v1/users/" + username).servletPath("/api/v1").cookie(access));
    }

    @Test
    @DisplayName("내 프로필: 프론트 UserDto 모양, id는 public_id, 집계 컬럼 그대로, isMe=true, 아이디 변경 시각은 보내지 않음")
    void myProfile() throws Exception {
        String publicId = jdbc.queryForObject("SELECT CAST(public_id AS TEXT) FROM users WHERE id = ?", String.class, me);

        getProfile("seoyeon")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicId))
                .andExpect(jsonPath("$.username").value("seoyeon"))
                .andExpect(jsonPath("$.nickname").value("서연"))
                .andExpect(jsonPath("$.name").value("서연"))
                .andExpect(jsonPath("$.bio").value("카페 투어"))
                .andExpect(jsonPath("$.region").value("서울"))
                .andExpect(jsonPath("$.postCount").value(7))
                .andExpect(jsonPath("$.followerCount").value(12))
                .andExpect(jsonPath("$.followingCount").value(3))
                .andExpect(jsonPath("$.isMe").value(true))
                .andExpect(jsonPath("$.isFollowing").value(false))
                .andExpect(jsonPath("$.isCreator").value(false))
                .andExpect(jsonPath("$.usernameChangeableAt").doesNotExist());
    }

    @Test
    @DisplayName("다른 회원 프로필: isMe=false, 이름이 없으면 name은 null이고 nickname은 아이디")
    void otherProfile() throws Exception {
        getProfile("minjun")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("minjun"))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.nickname").value("minjun"))
                .andExpect(jsonPath("$.isMe").value(false));
    }

    @Test
    @DisplayName("아이디는 대소문자를 무시하고, 응답에는 저장된 아이디를 돌려준다")
    void caseInsensitive() throws Exception {
        getProfile("SeoYeon")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("seoyeon"))
                .andExpect(jsonPath("$.isMe").value(true));
    }

    @Test
    @DisplayName("없는 아이디는 404 USER_NOT_FOUND")
    void notFound() throws Exception {
        getProfile("nobody")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WITHDRAWN_PENDING", "WITHDRAWN", "SUSPENDED", "BANNED"})
    @DisplayName("탈퇴 중 · 탈퇴 · 정지 · 영구 정지 회원의 프로필은 숨긴다 (404 USER_NOT_FOUND)")
    void hidesInactiveUsers(String userStatus) throws Exception {
        jdbc.update("UPDATE users SET status = ? WHERE id = ?", userStatus, other);

        getProfile("minjun")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/v1/users/seoyeon").servletPath("/api/v1")).andExpect(status().isUnauthorized());
    }
}
