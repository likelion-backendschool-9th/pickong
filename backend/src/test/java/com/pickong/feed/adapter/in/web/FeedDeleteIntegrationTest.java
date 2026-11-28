package com.pickong.feed.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import jakarta.persistence.EntityManager;
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
 * 본인 게시물 삭제를 실제 DB로 확인함
 * 본인이 지우는 게시물은 하드 삭제라 V4의 ON DELETE CASCADE로 댓글, 반응, 사진과 장소 연결이 같이 지워짐
 * (탈퇴 회원의 게시물은 30일 유예 뒤 소프트 삭제하는 별도 정책, #103)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FeedDeleteIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    private long author;
    private long other;

    @BeforeEach
    void setUp() {
        author = insertUser();
        other = insertUser();
    }

    @Test
    @DisplayName("작성자가 삭제하면 204, 게시물 행과 댓글, 좋아요, 북마크, 사진 연결이 모두 지워짐")
    void hardDeleteRemovesRowAndChildren() throws Exception {
        long feed = insertFeed(author);
        jdbc.update("INSERT INTO comments (feed_id, user_id, content) VALUES (?, ?, '댓글')", feed, other);
        jdbc.update("INSERT INTO feed_likes (feed_id, user_id) VALUES (?, ?)", feed, other);
        jdbc.update("INSERT INTO feed_bookmarks (feed_id, user_id) VALUES (?, ?)", feed, other);
        // feed_photos.media_file_id는 media_files FK(V25)라 실제 사진 행을 만들어 연결함
        long media = jdbc.queryForObject("INSERT INTO media_files (user_id, status) VALUES (?, 'UPLOADED') RETURNING id", Long.class, author);
        jdbc.update("INSERT INTO feed_photos (feed_id, media_file_id, sort_order) VALUES (?, ?, 0)", feed, media);

        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNoContent());
        flush();

        for (String table : List.of("comments", "feed_likes", "feed_bookmarks", "feed_photos")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE feed_id = ?", Integer.class, feed))
                    .as(table + "도 같이 지워져야 함").isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feeds WHERE id = ?", Integer.class, feed)).isZero();
    }

    @Test
    @DisplayName("신고된 게시물을 작성자가 지워도 신고와 접수 때 저장한 스냅샷은 남음")
    void reportSurvivesDeletion() throws Exception {
        long feed = insertFeed(author);
        mvc.perform(as(other, post("/api/v1/reports"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\": \"POST\", \"targetId\": " + feed + ", \"reason\": \"SPAM\"}"))
                .andExpect(status().isCreated());

        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNoContent());
        flush();

        assertThat(jdbc.queryForObject("SELECT snapshot FROM reports WHERE target_id = ? AND reporter_id = ?",
                String.class, feed, other)).contains("본문");
    }

    @Test
    @DisplayName("삭제한 게시물은 상세 404이고 프로필 피드 목록에서도 빠짐")
    void deletedFeedIsGone() throws Exception {
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
    @DisplayName("이미 삭제한 게시물을 다시 삭제하거나 수정하면 404")
    void deleteOrUpdateAfterDeletion() throws Exception {
        long feed = insertFeed(author);
        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNoContent());

        mvc.perform(as(author, delete("/api/v1/feeds/" + feed)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEED_NOT_FOUND"));
        mvc.perform(as(author, put("/api/v1/feeds/" + feed)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"되살리기\", \"visibility\": \"PUBLIC\"}")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("남의 게시물은 403이고 지워지지 않음")
    void othersFeed() throws Exception {
        long feed = insertFeed(author);

        mvc.perform(as(other, delete("/api/v1/feeds/" + feed)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEED_NOT_OWNER"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feeds WHERE id = ?", Integer.class, feed)).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자가 숨긴 게시물도 작성자는 삭제할 수 있음")
    void ownerCanDeleteHiddenFeed() throws Exception {
        long feed = insertFeed(author);
        jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", feed);

        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNoContent());
        flush();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feeds WHERE id = ?", Integer.class, feed)).isZero();
    }

    @Test
    @DisplayName("탈퇴 정책으로 소프트 삭제된 게시물(deleted_at)은 다시 삭제할 수 없음 (404, 행 유지)")
    void softDeletedFeedCannotBeDeletedAgain() throws Exception {
        long feed = insertFeed(author);
        jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", feed);

        mvc.perform(as(author, delete("/api/v1/feeds/" + feed))).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feeds WHERE id = ?", Integer.class, feed)).isEqualTo(1);
    }

    // 테스트 전체가 한 트랜잭션이라 JPA가 아직 보내지 않은 DELETE를 JDBC로 확인하기 전에 내보냄
    private void flush() {
        entityManager.flush();
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
