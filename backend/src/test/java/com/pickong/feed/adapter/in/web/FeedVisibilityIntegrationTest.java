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
import com.pickong.common.security.AdminPrincipal;
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

/**
 * 게시물 단건 접근의 공개 범위 검사를 실제 보안 필터와 DB(follows V20)로 확인함
 * 볼 수 없는 게시물은 어떤 API로 접근해도 404 FEED_NOT_FOUND여야 존재가 드러나지 않음
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FeedVisibilityIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long author;
    private long follower;
    private long stranger;

    @BeforeEach
    void setUp() {
        author = insertUser();
        follower = insertUser();
        stranger = insertUser();
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", follower, author);
    }

    @Test
    @DisplayName("상세 PUBLIC: 비로그인 포함 모두 200")
    void publicDetail() throws Exception {
        long feed = insertFeed("PUBLIC");

        mvc.perform(api(get("/api/v1/feeds/" + feed))).andExpect(status().isOk());
        mvc.perform(as(stranger, get("/api/v1/feeds/" + feed))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("상세 FOLLOWERS: 작성자와 팔로워만 200, 비로그인과 다른 회원은 404")
    void followersDetail() throws Exception {
        long feed = insertFeed("FOLLOWERS");

        mvc.perform(as(author, get("/api/v1/feeds/" + feed))).andExpect(status().isOk());
        mvc.perform(as(follower, get("/api/v1/feeds/" + feed))).andExpect(status().isOk());
        expectNotFound(api(get("/api/v1/feeds/" + feed)));
        expectNotFound(as(stranger, get("/api/v1/feeds/" + feed)));
    }

    @Test
    @DisplayName("상세 PRIVATE: 작성자만 200, 팔로워도 404")
    void privateDetail() throws Exception {
        long feed = insertFeed("PRIVATE");

        mvc.perform(as(author, get("/api/v1/feeds/" + feed))).andExpect(status().isOk());
        expectNotFound(as(follower, get("/api/v1/feeds/" + feed)));
        expectNotFound(api(get("/api/v1/feeds/" + feed)));
    }

    @Test
    @DisplayName("팔로우를 끊으면 바로 FOLLOWERS 게시물이 404")
    void unfollowHidesImmediately() throws Exception {
        long feed = insertFeed("FOLLOWERS");
        mvc.perform(as(follower, get("/api/v1/feeds/" + feed))).andExpect(status().isOk());

        jdbc.update("DELETE FROM follows WHERE follower_id = ? AND followee_id = ?", follower, author);

        expectNotFound(as(follower, get("/api/v1/feeds/" + feed)));
    }

    @Test
    @DisplayName("관리자는 PRIVATE 게시물도 200")
    void adminReadsPrivate() throws Exception {
        long feed = insertFeed("PRIVATE");
        long adminId = jdbc.queryForObject("""
                INSERT INTO admins(username, password_hash, role, status)
                VALUES (?, 'test-only', 'OPERATOR', 'ACTIVE') RETURNING id
                """, Long.class, "v" + UUID.randomUUID().toString().substring(0, 8));

        mvc.perform(api(get("/api/v1/feeds/" + feed)).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                        new AdminPrincipal(adminId), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("댓글 목록: FOLLOWERS 게시물은 팔로워 200, 다른 회원 404")
    void comments() throws Exception {
        long feed = insertFeed("FOLLOWERS");

        mvc.perform(as(follower, get("/api/v1/feeds/" + feed + "/comments").param("limit", "10")))
                .andExpect(status().isOk());
        expectNotFound(as(stranger, get("/api/v1/feeds/" + feed + "/comments").param("limit", "10")));
    }

    @Test
    @DisplayName("좋아요 추가: FOLLOWERS 게시물은 팔로워 201, 다른 회원 404이고 저장 안 됨")
    void like() throws Exception {
        long feed = insertFeed("FOLLOWERS");

        mvc.perform(as(follower, post("/api/v1/feeds/" + feed + "/likes"))).andExpect(status().isCreated());
        expectNotFound(as(stranger, post("/api/v1/feeds/" + feed + "/likes")));

        assertThat(jdbc.queryForList("SELECT user_id FROM feed_likes WHERE feed_id = ?", Long.class, feed))
                .containsExactly(follower);
    }

    @Test
    @DisplayName("좋아요 취소: 볼 수 없는 게시물이면 LIKE_NOT_FOUND가 아니라 FEED_NOT_FOUND (존재를 숨김)")
    void unlikeOnInvisibleFeed() throws Exception {
        long feed = insertFeed("PRIVATE");

        // 좋아요를 누른 적이 없는 회원이어도 LIKE_NOT_FOUND가 나가면 게시물이 있다는 게 드러남
        expectNotFound(as(stranger, delete("/api/v1/feeds/" + feed + "/likes")));
    }

    @Test
    @DisplayName("PUBLIC → PRIVATE로 바뀌면 전에 누른 좋아요는 남고, 누른 사람도 취소할 수 없음 (정책)")
    void likeStaysAfterPrivate() throws Exception {
        long feed = insertFeed("PUBLIC");
        mvc.perform(as(stranger, post("/api/v1/feeds/" + feed + "/likes"))).andExpect(status().isCreated());

        jdbc.update("UPDATE feeds SET visibility = 'PRIVATE' WHERE id = ?", feed);

        expectNotFound(as(stranger, delete("/api/v1/feeds/" + feed + "/likes")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_likes WHERE feed_id = ?", Integer.class, feed)).isEqualTo(1);
    }

    @Test
    @DisplayName("게시물 수정: 남의 PRIVATE 게시물은 403 FEED_NOT_OWNER가 아니라 404 (존재를 숨김)")
    void updateOthersPrivateFeed() throws Exception {
        long feed = insertFeed("PRIVATE");

        expectNotFound(as(stranger, put("/api/v1/feeds/" + feed)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"바꿈\", \"visibility\": \"PUBLIC\"}")));
    }

    @Test
    @DisplayName("댓글 작성: PRIVATE 게시물은 팔로워도 404이고 저장 안 됨")
    void commentOnPrivate() throws Exception {
        long feed = insertFeed("PRIVATE");

        expectNotFound(as(follower, post("/api/v1/feeds/" + feed + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"댓글\"}")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE feed_id = ?", Integer.class, feed)).isZero();
    }

    // ── PR #176 리뷰 3번: 관리자 여부는 인증 주체(AdminPrincipal)로만 정해짐 ──────────

    @Test
    @DisplayName("일반 회원이 ROLE_ADMIN 권한이나 admin=true 파라미터를 붙여도 PRIVATE 게시물은 404")
    void adminCannotBeForgedByMember() throws Exception {
        long feed = insertFeed("PRIVATE");

        expectNotFound(api(get("/api/v1/feeds/" + feed)).param("admin", "true")
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(stranger), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))))));
        expectNotFound(as(stranger, get("/api/v1/feeds/" + feed + "/comments").param("limit", "10").param("admin", "true")));
    }

    // ── PR #176 리뷰 6번: 나머지 단건 접근 API ──────────────────────────────

    @Test
    @DisplayName("대댓글 목록: 볼 수 없는 게시물이면 404")
    void repliesOnInvisibleFeed() throws Exception {
        long feed = insertFeed("PRIVATE");
        long comment = insertComment(feed, author);

        expectNotFound(as(stranger, get("/api/v1/feeds/" + feed + "/comments/" + comment + "/replies").param("limit", "10")));
    }

    @Test
    @DisplayName("북마크 추가와 취소: 볼 수 없는 게시물이면 404이고 북마크는 그대로")
    void bookmarkOnInvisibleFeed() throws Exception {
        long feed = insertFeed("FOLLOWERS");
        jdbc.update("INSERT INTO feed_bookmarks (feed_id, user_id) VALUES (?, ?)", feed, follower);

        expectNotFound(as(stranger, post("/api/v1/feeds/" + feed + "/bookmarks")));
        expectNotFound(as(stranger, delete("/api/v1/feeds/" + feed + "/bookmarks")));

        assertThat(jdbc.queryForList("SELECT user_id FROM feed_bookmarks WHERE feed_id = ?", Long.class, feed))
                .containsExactly(follower);
    }

    @Test
    @DisplayName("댓글 좋아요 추가와 취소: 볼 수 없는 게시물이면 404이고 좋아요는 그대로")
    void commentLikeOnInvisibleFeed() throws Exception {
        long feed = insertFeed("PUBLIC");
        long comment = insertComment(feed, author);
        mvc.perform(as(stranger, post("/api/v1/feeds/" + feed + "/comments/" + comment + "/likes")))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE feeds SET visibility = 'PRIVATE' WHERE id = ?", feed);

        expectNotFound(as(follower, post("/api/v1/feeds/" + feed + "/comments/" + comment + "/likes")));
        expectNotFound(as(stranger, delete("/api/v1/feeds/" + feed + "/comments/" + comment + "/likes")));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_likes WHERE comment_id = ?", Integer.class, comment))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("내 댓글 수정과 삭제: 게시물이 PRIVATE가 되면 404이고 댓글은 그대로 (정책)")
    void ownCommentOnInvisibleFeed() throws Exception {
        long feed = insertFeed("PUBLIC");
        long comment = insertComment(feed, stranger);
        jdbc.update("UPDATE feeds SET visibility = 'PRIVATE' WHERE id = ?", feed);

        expectNotFound(as(stranger, put("/api/v1/feeds/" + feed + "/comments/" + comment)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"바꿈\"}")));
        expectNotFound(as(stranger, delete("/api/v1/feeds/" + feed + "/comments/" + comment)));

        assertThat(jdbc.queryForMap("SELECT content, deleted_at FROM comments WHERE id = ?", comment))
                .containsEntry("content", "댓글").containsEntry("deleted_at", null);
    }

    @Test
    @DisplayName("게시물 삭제: 남의 PRIVATE 게시물은 403이 아니라 404이고 게시물은 그대로")
    void deleteOthersPrivateFeed() throws Exception {
        long feed = insertFeed("PRIVATE");

        expectNotFound(as(stranger, delete("/api/v1/feeds/" + feed)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feeds WHERE id = ?", Integer.class, feed)).isEqualTo(1);
    }

    @Test
    @DisplayName("숨김 게시물: 작성자와 관리자는 열람, 팔로워와 비로그인은 404, 작성자도 좋아요는 404")
    void hiddenFeed() throws Exception {
        long feed = insertFeed("PUBLIC");
        jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", feed);

        mvc.perform(as(author, get("/api/v1/feeds/" + feed))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/feeds/" + feed))).andExpect(status().isOk());
        expectNotFound(as(follower, get("/api/v1/feeds/" + feed)));
        expectNotFound(api(get("/api/v1/feeds/" + feed)));
        expectNotFound(as(author, post("/api/v1/feeds/" + feed + "/likes")));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_likes WHERE feed_id = ?", Integer.class, feed)).isZero();
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        long adminId = jdbc.queryForObject("""
                INSERT INTO admins(username, password_hash, role, status)
                VALUES (?, 'test-only', 'OPERATOR', 'ACTIVE') RETURNING id
                """, Long.class, "v" + UUID.randomUUID().toString().substring(0, 8));
        return api(request).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(adminId), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))));
    }

    private long insertComment(long feedId, long userId) {
        return jdbc.queryForObject("INSERT INTO comments (feed_id, user_id, content) VALUES (?, ?, '댓글') RETURNING id",
                Long.class, feedId, userId);
    }

    private void expectNotFound(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEED_NOT_FOUND"));
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder request) {
        return request.servletPath("/api/v1").with(csrf());
    }

    private static MockHttpServletRequestBuilder as(long userId, MockHttpServletRequestBuilder request) {
        return api(request).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }

    private long insertFeed(String visibility) {
        return jdbc.queryForObject("INSERT INTO feeds (user_id, content, visibility) VALUES (?, '본문', ?) RETURNING id",
                Long.class, author, visibility);
    }

    private long insertUser() {
        String username = "v" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
