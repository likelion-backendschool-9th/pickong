package com.pickong.feed.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
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

/**
 * 삭제된 댓글의 수정 · 재삭제를 실제 API와 DB로 확인함 (#198)
 * 응답 코드뿐 아니라 막혔을 때 DB 값(본문, 삭제 시각)이 그대로인지까지 봄
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CommentStateIntegrationTest {

    // 삭제 시각이 덮어써지지 않았는지 비교하려고 지난 시각으로 고정함
    private static final OffsetDateTime DELETED_BEFORE = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    // 서비스의 JPA 변경은 같은 테스트 트랜잭션 안에서 바로 DB에 반영되지 않아 JDBC로 읽기 전에 flush해야 됨
    @Autowired
    EntityManager entityManager;

    private long author;
    private long stranger;
    private long feed;

    @BeforeEach
    void setUp() {
        author = insertUser();
        stranger = insertUser();
        feed = jdbc.queryForObject("INSERT INTO feeds (user_id, content, visibility) VALUES (?, '본문', 'PUBLIC') RETURNING id",
                Long.class, author);
    }

    @Test
    @DisplayName("삭제된 내 댓글 수정: 404 COMMENT_NOT_FOUND이고 본문은 그대로")
    void updateDeletedOwnComment() throws Exception {
        long comment = insertComment(stranger);
        jdbc.update("UPDATE comments SET deleted_at = ? WHERE id = ?", DELETED_BEFORE, comment);

        mvc.perform(updateRequest(stranger, comment))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));

        assertThat(contentOf(comment)).isEqualTo("댓글");
    }

    @Test
    @DisplayName("남의 삭제된 댓글 수정 · 삭제: 403이 아니라 404 (댓글이 있었다는 사실을 드러내지 않음)")
    void othersDeletedComment() throws Exception {
        long comment = insertComment(author);
        jdbc.update("UPDATE comments SET deleted_at = ? WHERE id = ?", DELETED_BEFORE, comment);

        mvc.perform(updateRequest(stranger, comment))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
        mvc.perform(as(stranger, delete(commentPath(comment))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 삭제된 댓글 다시 삭제: 404이고 삭제 시각이 새로 덮어써지지 않음")
    void deleteAlreadyDeletedComment() throws Exception {
        long comment = insertComment(stranger);
        jdbc.update("UPDATE comments SET deleted_at = ? WHERE id = ?", DELETED_BEFORE, comment);

        mvc.perform(as(stranger, delete(commentPath(comment))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));

        entityManager.flush();
        assertThat(deletedAtOf(comment).toInstant()).isEqualTo(DELETED_BEFORE.toInstant());
    }

    @Test
    @DisplayName("관리자가 숨긴 내 댓글: 작성자는 삭제할 수 있음 (204)")
    void hiddenOwnComment() throws Exception {
        long comment = insertComment(stranger);
        jdbc.update("UPDATE comments SET hidden_at = now() WHERE id = ?", comment);

        mvc.perform(as(stranger, delete(commentPath(comment)))).andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(deletedAtOf(comment)).isNotNull();
    }

    @Test
    @DisplayName("정상 댓글은 지금처럼 수정 200, 삭제 204")
    void normalComment() throws Exception {
        long comment = insertComment(stranger);

        mvc.perform(updateRequest(stranger, comment)).andExpect(status().isOk());
        entityManager.flush();
        assertThat(contentOf(comment)).isEqualTo("바꿈");

        mvc.perform(as(stranger, delete(commentPath(comment)))).andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(deletedAtOf(comment)).isNotNull();
    }

    private MockHttpServletRequestBuilder updateRequest(long userId, long commentId) {
        return as(userId, put(commentPath(commentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"바꿈\"}"));
    }

    private String commentPath(long commentId) {
        return "/api/v1/feeds/" + feed + "/comments/" + commentId;
    }

    private String contentOf(long commentId) {
        return jdbc.queryForObject("SELECT content FROM comments WHERE id = ?", String.class, commentId);
    }

    private OffsetDateTime deletedAtOf(long commentId) {
        return jdbc.queryForObject("SELECT deleted_at FROM comments WHERE id = ?", OffsetDateTime.class, commentId);
    }

    private long insertComment(long userId) {
        return jdbc.queryForObject("INSERT INTO comments (feed_id, user_id, content) VALUES (?, ?, '댓글') RETURNING id",
                Long.class, feed, userId);
    }

    private static MockHttpServletRequestBuilder as(long userId, MockHttpServletRequestBuilder request) {
        return request.servletPath("/api/v1").with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }

    private long insertUser() {
        String username = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long id = jdbc.queryForObject("INSERT INTO users (username, email) VALUES (?, ?) RETURNING id",
                Long.class, username, username + "@example.com");
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둠
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }
}
