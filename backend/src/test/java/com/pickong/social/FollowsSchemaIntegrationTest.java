package com.pickong.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * V20 follows 테이블: 제약 조건, 팔로잉 피드 조회, 팔로우 API를 실제 DB와 보안 필터로 확인함
 * 제약 위반은 트랜잭션을 중단시키므로 테스트마다 위반 문장을 마지막에 둠. PostGIS 컨테이너가 필요함
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FollowsSchemaIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private long followee;
    private long stranger;

    @BeforeEach
    void setUp() {
        me = insertUser(randomUsername());
        followee = insertUser(randomUsername());
        stranger = insertUser(randomUsername());
    }

    @Test
    @DisplayName("팔로우 관계를 저장하고 생성 시각이 채워짐")
    void savesFollow() {
        follow(me, followee);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE follower_id = ? AND followee_id = ? AND created_at IS NOT NULL",
                Integer.class, me, followee);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사람을 두 번 팔로우하면 UNIQUE 제약에 걸림")
    void rejectsDuplicateFollow() {
        follow(me, followee);

        assertThatThrownBy(() -> follow(me, followee)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("자기 자신을 팔로우하면 CHECK 제약에 걸림")
    void rejectsSelfFollow() {
        assertThatThrownBy(() -> follow(me, me)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("없는 회원 id로 팔로우하면 FK 제약에 걸림")
    void rejectsUnknownUser() {
        assertThatThrownBy(() -> follow(me, Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("팔로잉 피드: 500 없이 팔로우한 사람의 글만 최신순으로 돌려줌")
    void followingFeedReadsFollows() throws Exception {
        follow(me, followee);
        insertFeed(followee, "팔로우한 사람 글 1");
        insertFeed(stranger, "모르는 사람 글");
        insertFeed(followee, "팔로우한 사람 글 2");

        mvc.perform(get("/api/v1/feeds").servletPath("/api/v1")
                        .param("tab", "following")
                        .param("limit", "10")
                        .with(authentication(member(me))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeds[*].content", contains("팔로우한 사람 글 2", "팔로우한 사람 글 1")))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("팔로우 API: 본문에 남의 id를 넣어도 로그인 사용자로 저장하고 팔로우 수를 올림")
    void followApiUsesLoginUser() throws Exception {
        String followeeName = jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, followee);

        mvc.perform(post("/api/v1/users/" + followeeName + "/follow").servletPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + stranger + "}")
                        .with(csrf())
                        .with(authentication(member(me))))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForList("SELECT follower_id FROM follows WHERE followee_id = ?", Long.class, followee))
                .containsExactly(me);
        assertThat(jdbc.queryForObject("SELECT following_count FROM users WHERE id = ?", Long.class, me)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT follower_count FROM users WHERE id = ?", Long.class, followee)).isEqualTo(1L);
    }

    @Test
    @DisplayName("팔로우 API: 로그인하지 않으면 401")
    void followApiRequiresLogin() throws Exception {
        String followeeName = jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, followee);

        mvc.perform(post("/api/v1/users/" + followeeName + "/follow").servletPath("/api/v1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private void follow(long followerId, long followeeId) {
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", followerId, followeeId);
    }

    private static String randomUsername() {
        return "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long insertUser(String username) {
        long id = jdbc.queryForObject("INSERT INTO users (username, email) VALUES (?, ?) RETURNING id",
                Long.class, username, username + "@example.com");
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둠
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private void insertFeed(long userId, String content) {
        jdbc.update("INSERT INTO feeds (user_id, content, visibility) VALUES (?, ?, 'PUBLIC')", userId, content);
    }

    private static UsernamePasswordAuthenticationToken member(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
