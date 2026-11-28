package com.pickong.feed.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 댓글 응답이 프론트 CommentDto 모양(작성자 요약, postId)으로 나가는지 실제 DB로 확인함 (#207) */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CommentResponseContractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long first;
    private long second;
    private long feed;

    @BeforeEach
    void setUp() {
        first = insertUser("첫째");
        second = insertUser(null);
        feed = jdbc.queryForObject("INSERT INTO feeds (user_id, content, visibility) VALUES (?, '본문', 'PUBLIC') RETURNING id",
                Long.class, first);
    }

    @Test
    @DisplayName("댓글 목록: 댓글마다 자기 작성자가 붙고, 내부 userId는 없음")
    void listAttachesEachAuthor() throws Exception {
        long byFirst = insertComment(first, "첫째 댓글", null);
        long bySecond = insertComment(second, "둘째 댓글", null);

        String body = getBody(as(second, get("/api/v1/feeds/" + feed + "/comments").param("limit", "10")));

        assertThat(authorUsername(body, byFirst)).isEqualTo(usernameOf(first));
        assertThat(authorUsername(body, bySecond)).isEqualTo(usernameOf(second));
        List<String> nicknames = JsonPath.read(body, "$.comments[?(@.id == '" + byFirst + "')].author.nickname");
        assertThat(nicknames).containsExactly("첫째");
        List<String> postIds = JsonPath.read(body, "$.comments[*].postId");
        assertThat(postIds).containsOnly(String.valueOf(feed));
        assertThat(body).doesNotContain("\"userId\"");
    }

    @Test
    @DisplayName("대댓글 목록과 작성 응답에도 작성자가 붙음")
    void repliesAndCreateHaveAuthor() throws Exception {
        long parent = insertComment(first, "부모", null);
        long reply = insertComment(second, "답글", parent);

        String replies = getBody(as(first, get("/api/v1/feeds/" + feed + "/comments/" + parent + "/replies").param("limit", "10")));
        assertThat(authorUsername(replies, reply)).isEqualTo(usernameOf(second));
        List<String> parentIds = JsonPath.read(replies, "$.comments[*].parentId");
        assertThat(parentIds).containsExactly(String.valueOf(parent));

        String created = mvc.perform(as(second, post("/api/v1/feeds/" + feed + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"새 댓글\"}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(created, "$.author.username")).isEqualTo(usernameOf(second));
    }

    private String authorUsername(String body, long commentId) {
        List<String> usernames = JsonPath.read(body, "$.comments[?(@.id == '" + commentId + "')].author.username");
        assertThat(usernames).hasSize(1);
        return usernames.get(0);
    }

    private String getBody(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long insertComment(long userId, String content, Long parentId) {
        return jdbc.queryForObject("INSERT INTO comments (feed_id, user_id, content, parent_id) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, feed, userId, content, parentId);
    }

    private String usernameOf(long userId) {
        return jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
    }

    private long insertUser(String displayName) {
        String username = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long id = jdbc.queryForObject("INSERT INTO users (username, email, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, username + "@example.com", displayName);
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둠
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private static MockHttpServletRequestBuilder as(long userId, MockHttpServletRequestBuilder request) {
        return request.servletPath("/api/v1").with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }
}
