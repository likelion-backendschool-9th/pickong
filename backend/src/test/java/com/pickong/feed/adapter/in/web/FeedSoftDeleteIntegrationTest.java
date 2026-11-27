package com.pickong.feed.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import java.util.List;
import java.util.Map;
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
 * 게시물 삭제가 행을 지우지 않고 deleted_at만 채우는지 실제 DB로 확인함
 * 물리 삭제였을 때는 V4의 ON DELETE CASCADE로 댓글, 좋아요, 북마크, 사진, 장소 연결이 같이 사라졌음
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FeedSoftDeleteIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long author;
    private long other;

    @BeforeEach
    void setUp() {
        author = insertUser();
        other = insertUser();
    }

    @Test
    @DisplayName("작성자가 삭제하면 204, 행은 남고 deleted_at만 채워짐. 댓글, 좋아요, 북마크, 사진, 장소 연결도 그대로")
    void softDeleteKeepsRowAndChildren() throws Exception {
        long feed = insertFeed(author);
        jdbc.update("INSERT INTO comments (feed_id, user_id, content) VALUES (?, ?, '댓글')", feed, other);
        jdbc.update("INSERT INTO feed_likes (feed_id, user_id) VALUES (?, ?)", feed, other);
        jdbc.update("INSERT INTO feed_bookmarks (feed_id, user_id) VALUES (?, ?)", feed, other);
        jdbc.update("INSERT INTO feed_photos (feed_id, media_file_id, sort_order) VALUES (?, 1, 0)", feed);

        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM feeds WHERE id = ?", Boolean.class, feed)).isTrue();
        for (String table : List.of("comments", "feed_likes", "feed_bookmarks", "feed_photos")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE feed_id = ?", Integer.class, feed))
                    .as(table + "는 남아 있어야 함").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("삭제한 게시물은 상세 404이고 피드 목록에도 안 나옴")
    void deletedFeedIsHidden() throws Exception {
        long feed = insertFeed(author);
        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNoContent());

        mvc.perform(as(author, get("/api/v1/feeds/" + feed)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEED_NOT_FOUND"));
        String username = jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, author);
        mvc.perform(as(author, get("/api/v1/users/" + username + "/feeds").param("limit", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeds").isEmpty());
    }

    @Test
    @DisplayName("이미 삭제한 게시물을 다시 삭제하면 404, 삭제 시각은 처음 그대로")
    void deleteTwice() throws Exception {
        long feed = insertFeed(author);
        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNoContent());
        Object firstDeletedAt = jdbc.queryForObject("SELECT deleted_at FROM feeds WHERE id = ?", Object.class, feed);

        mvc.perform(as(author, delete("/api/v1/feeds/" + feed)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEED_NOT_FOUND"));

        assertThat(jdbc.queryForObject("SELECT deleted_at FROM feeds WHERE id = ?", Object.class, feed))
                .isEqualTo(firstDeletedAt);
    }

    @Test
    @DisplayName("삭제한 게시물은 행이 남아 있어도 수정할 수 없음 (404, 내용 그대로)")
    void cannotUpdateDeletedFeed() throws Exception {
        long feed = insertFeed(author);
        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNoContent());

        mvc.perform(as(author, put("/api/v1/feeds/" + feed)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"되살리기\", \"visibility\": \"PUBLIC\"}")))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT content FROM feeds WHERE id = ?", String.class, feed)).isEqualTo("본문");
    }

    @Test
    @DisplayName("남의 게시물은 403이고 삭제되지 않음")
    void othersFeed() throws Exception {
        long feed = insertFeed(author);

        mvc.perform(as(other, delete("/api/v1/feeds/" + feed)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEED_NOT_OWNER"));

        assertThat(jdbc.queryForObject("SELECT deleted_at FROM feeds WHERE id = ?", Object.class, feed)).isNull();
    }

    @Test
    @DisplayName("관리자가 숨긴 게시물도 작성자는 삭제할 수 있고, 숨김 기록은 남음")
    void ownerCanDeleteHiddenFeed() throws Exception {
        long feed = insertFeed(author);
        jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", feed);

        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap("SELECT hidden_at, deleted_at FROM feeds WHERE id = ?", feed);
        assertThat(row.get("hidden_at")).isNotNull();
        assertThat(row.get("deleted_at")).isNotNull();
    }

    private static MockHttpServletRequestBuilder as(long userId, MockHttpServletRequestBuilder request) {
        return request.servletPath("/api/v1").with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }

    private long insertFeed(long userId) {
        return jdbc.queryForObject("INSERT INTO feeds (user_id, content, visibility) VALUES (?, '본문', 'PUBLIC') RETURNING id",
                Long.class, userId);
    }

    private long insertUser() {
        String username = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
