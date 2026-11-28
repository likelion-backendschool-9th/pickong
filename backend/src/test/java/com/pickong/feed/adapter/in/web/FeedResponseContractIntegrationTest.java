package com.pickong.feed.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시물 응답이 프론트 PostDto 모양으로 나가는지 실제 DB(users · media_files · places · 반응)로 확인함 (#206)
 * FeedViewQueryAdapter의 SQL 조건(사진 상태, 장소 공개 범위 · 삭제, 보는 사람 기준 반응)을 함께 검증함
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FeedResponseContractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long author;
    private String authorPublicId;
    private long viewer;

    @BeforeEach
    void setUp() {
        author = insertUser("작성자이름", "https://img.example/author.png", "서울");
        authorPublicId = jdbc.queryForObject("SELECT public_id::text FROM users WHERE id = ?", String.class, author);
        viewer = insertUser(null, null, null);
    }

    @Test
    @DisplayName("상세: 작성자는 공개 id · 아이디 · 표시 이름 · 프로필 사진 · 지역, 내부 userId는 없음")
    void author() throws Exception {
        long feed = insertFeed();

        String body = getBody(as(viewer, get("/api/v1/feeds/" + feed)));

        assertThat((String) JsonPath.read(body, "$.id")).isEqualTo(String.valueOf(feed));
        Map<String, Object> responseAuthor = JsonPath.read(body, "$.author");
        assertThat(responseAuthor)
                .containsEntry("id", authorPublicId)
                .containsEntry("nickname", "작성자이름")
                .containsEntry("profileImageUrl", "https://img.example/author.png")
                .containsEntry("region", "서울")
                .doesNotContainKey("email");
        assertThat(body).doesNotContain("\"userId\"");
    }

    @Test
    @DisplayName("이름이 없는 작성자의 표시 이름은 아이디")
    void nicknameFallsBackToUsername() throws Exception {
        long nameless = insertUser(null, null, null);
        long feed = jdbc.queryForObject("INSERT INTO feeds (user_id, content, visibility) VALUES (?, '본문', 'PUBLIC') RETURNING id",
                Long.class, nameless);
        String username = jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, nameless);

        String body = getBody(as(viewer, get("/api/v1/feeds/" + feed)));

        assertThat((String) JsonPath.read(body, "$.author.nickname")).isEqualTo(username);
    }

    @Test
    @DisplayName("사진: 확인이 끝난 사진(UPLOADED, PROCESSED)만 sort_order 순서로, 업로드 중 · 실패 사진은 뺌")
    void photos() throws Exception {
        long feed = insertFeed();
        insertPhoto(feed, insertMedia("UPLOADED", "https://cdn.example/second.jpg"), 1);
        insertPhoto(feed, insertMedia("PROCESSED", "https://cdn.example/first.jpg"), 0);
        insertPhoto(feed, insertMedia("PENDING", null), 2);
        insertPhoto(feed, insertMedia("FAILED", null), 3);

        String body = getBody(as(viewer, get("/api/v1/feeds/" + feed)));

        List<String> urls = JsonPath.read(body, "$.photos[*].url");
        assertThat(urls).containsExactly("https://cdn.example/first.jpg", "https://cdn.example/second.jpg");
    }

    @Test
    @DisplayName("장소: 삭제된 장소와 남의 비공개 장소는 빼고, 장소가 1개면 모든 사진에 그 장소를 붙임")
    void places() throws Exception {
        long feed = insertFeed();
        insertPhoto(feed, insertMedia("UPLOADED", "https://cdn.example/p.jpg"), 0);
        long publicPlace = insertPlace("공개 카페", "PUBLIC", null, false);
        long deletedPlace = insertPlace("삭제된 곳", "PUBLIC", null, true);
        long othersPrivate = insertPlace("남의 비공개", "PRIVATE", author, false);
        for (long place : List.of(publicPlace, deletedPlace, othersPrivate)) {
            jdbc.update("INSERT INTO feed_places (feed_id, place_id) VALUES (?, ?)", feed, place);
        }
        String publicPlaceId = jdbc.queryForObject("SELECT public_id::text FROM places WHERE id = ?", String.class, publicPlace);

        String body = getBody(as(viewer, get("/api/v1/feeds/" + feed)));

        List<String> names = JsonPath.read(body, "$.places[*].name");
        assertThat(names).containsExactly("공개 카페");
        assertThat((String) JsonPath.read(body, "$.places[0].id")).isEqualTo(publicPlaceId);
        assertThat((String) JsonPath.read(body, "$.photos[0].placeId")).isEqualTo(publicPlaceId);

        // 비공개 장소의 주인이 보면 그 장소도 보임
        List<String> ownerSees = JsonPath.read(getBody(as(author, get("/api/v1/feeds/" + feed))), "$.places[*].name");
        assertThat(ownerSees).containsExactlyInAnyOrder("공개 카페", "남의 비공개");
    }

    @Test
    @DisplayName("장소가 여러 개면 사진별 장소를 알 수 없어서 photos[].placeId는 null")
    void multiplePlacesLeavePhotoPlaceEmpty() throws Exception {
        long feed = insertFeed();
        insertPhoto(feed, insertMedia("UPLOADED", "https://cdn.example/m.jpg"), 0);
        jdbc.update("INSERT INTO feed_places (feed_id, place_id) VALUES (?, ?)", feed, insertPlace("A", "PUBLIC", null, false));
        jdbc.update("INSERT INTO feed_places (feed_id, place_id) VALUES (?, ?)", feed, insertPlace("B", "PUBLIC", null, false));

        String body = getBody(as(viewer, get("/api/v1/feeds/" + feed)));

        assertThat((List<?>) JsonPath.read(body, "$.places")).hasSize(2);
        assertThat((Object) JsonPath.read(body, "$.photos[0].placeId")).isNull();
    }

    @Test
    @DisplayName("좋아요 · 북마크 여부는 보는 사람 기준, 목록에서도 게시물마다 맞게 붙고 비로그인은 false")
    void reactionsPerViewerAndPerFeed() throws Exception {
        long liked = insertFeed();
        long bookmarked = insertFeed();
        jdbc.update("INSERT INTO feed_likes (feed_id, user_id) VALUES (?, ?)", liked, viewer);
        jdbc.update("INSERT INTO feed_bookmarks (feed_id, user_id) VALUES (?, ?)", bookmarked, viewer);
        // 다른 사람의 반응은 보는 사람 값에 섞이지 않아야 됨
        jdbc.update("INSERT INTO feed_bookmarks (feed_id, user_id) VALUES (?, ?)", liked, author);

        String page = getBody(as(viewer, get("/api/v1/users/" + usernameOf(author) + "/feeds").param("limit", "10")));
        assertThat(flag(page, liked, "isLiked")).isTrue();
        assertThat(flag(page, liked, "isBookmarked")).isFalse();
        assertThat(flag(page, bookmarked, "isLiked")).isFalse();
        assertThat(flag(page, bookmarked, "isBookmarked")).isTrue();

        String anonymous = getBody(api(get("/api/v1/feeds/" + liked)));
        assertThat((Boolean) JsonPath.read(anonymous, "$.isLiked")).isFalse();
        assertThat((Boolean) JsonPath.read(anonymous, "$.isBookmarked")).isFalse();
    }

    private boolean flag(String pageBody, long feedId, String field) {
        List<Boolean> values = JsonPath.read(pageBody, "$.feeds[?(@.id == '" + feedId + "')]." + field);
        assertThat(values).hasSize(1);
        return values.get(0);
    }

    private String getBody(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long insertFeed() {
        return jdbc.queryForObject("INSERT INTO feeds (user_id, content, visibility) VALUES (?, '본문', 'PUBLIC') RETURNING id",
                Long.class, author);
    }

    private long insertMedia(String status, String url) {
        return jdbc.queryForObject("INSERT INTO media_files (user_id, status, storage_key, url) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, author, status, "feeds/" + UUID.randomUUID() + ".jpg", url);
    }

    private void insertPhoto(long feedId, long mediaId, int sortOrder) {
        jdbc.update("INSERT INTO feed_photos (feed_id, media_file_id, sort_order) VALUES (?, ?, ?)", feedId, mediaId, sortOrder);
    }

    private long insertPlace(String name, String visibility, Long ownerId, boolean deleted) {
        return jdbc.queryForObject("""
                INSERT INTO places (name, location, source_type, visibility, owner_id, deleted_at)
                VALUES (?, ST_SetSRID(ST_MakePoint(126.97, 37.56), 4326)::geography, 'CUSTOM', ?, ?, CASE WHEN ? THEN now() END)
                RETURNING id
                """, Long.class, name, visibility, ownerId, deleted);
    }

    private String usernameOf(long userId) {
        return jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
    }

    private long insertUser(String displayName, String profileImageUrl, String region) {
        String username = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long id = jdbc.queryForObject("""
                INSERT INTO users (username, email, display_name, profile_image_url, region) VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, username, username + "@example.com", displayName, profileImageUrl, region);
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둠
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder request) {
        return request.servletPath("/api/v1").with(csrf());
    }

    private static MockHttpServletRequestBuilder as(long userId, MockHttpServletRequestBuilder request) {
        return api(request).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }
}
