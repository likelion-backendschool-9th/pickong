package com.pickong.feed.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목록 API 네 개(팔로잉 탭, 추천 탭, 프로필 피드, 피드 지도)의 공개 범위를 실제 DB(follows V20)로 확인함
 * 등장인물: 나(me), 내가 팔로우하는 사람(followee), 모르는 사람(stranger)
 * 각자 PUBLIC, FOLLOWERS, PRIVATE 게시물을 하나씩 가짐. content는 "주인-공개범위" 모양이라 결과를 이름으로 비교함
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FeedListVisibilityIntegrationTest {

    private static final List<String> VISIBILITIES = List.of("PUBLIC", "FOLLOWERS", "PRIVATE");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private long followee;
    private long stranger;
    private String followeeName;
    private long place;

    @BeforeEach
    void setUp() {
        me = insertUser();
        followee = insertUser();
        stranger = insertUser();
        followeeName = jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, followee);
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", me, followee);
        place = jdbc.queryForObject("""
                INSERT INTO places (source_type, name, location, category, visibility)
                VALUES ('SYSTEM', '지도 확인용', ST_SetSRID(ST_MakePoint(127.0, 37.5), 4326)::geography, 'CAFE', 'PUBLIC')
                RETURNING id
                """, Long.class);
        for (String visibility : VISIBILITIES) {
            insertFeed(me, "me-" + visibility, visibility);
            insertFeed(followee, "followee-" + visibility, visibility);
            insertFeed(stranger, "stranger-" + visibility, visibility);
        }
    }

    @Test
    @DisplayName("팔로잉 탭: 내 글 전부 + 팔로우한 사람의 PUBLIC, FOLLOWERS (팔로우한 사람의 PRIVATE, 모르는 사람 글은 제외)")
    void followingTab() throws Exception {
        assertThat(contents(as(me, get("/api/v1/feeds").param("tab", "following").param("limit", "50"))))
                .containsExactlyInAnyOrder("me-PUBLIC", "me-FOLLOWERS", "me-PRIVATE",
                        "followee-PUBLIC", "followee-FOLLOWERS");
    }

    @Test
    @DisplayName("팔로잉 탭: 아무도 팔로우하지 않으면 내 글만")
    void followingTabWithoutFollowees() throws Exception {
        assertThat(contents(as(stranger, get("/api/v1/feeds").param("tab", "following").param("limit", "50"))))
                .containsExactlyInAnyOrder("stranger-PUBLIC", "stranger-FOLLOWERS", "stranger-PRIVATE");
    }

    @Test
    @DisplayName("팔로잉 탭: 팔로우를 끊으면 다음 요청부터 그 사람 글이 빠짐")
    void followingTabAfterUnfollow() throws Exception {
        jdbc.update("DELETE FROM follows WHERE follower_id = ? AND followee_id = ?", me, followee);

        assertThat(contents(as(me, get("/api/v1/feeds").param("tab", "following").param("limit", "50"))))
                .containsExactlyInAnyOrder("me-PUBLIC", "me-FOLLOWERS", "me-PRIVATE");
    }

    @Test
    @DisplayName("추천 탭: 다른 사람의 PUBLIC만 (내 글, 팔로우한 사람의 FOLLOWERS는 제외)")
    void recommendedTab() throws Exception {
        assertThat(contents(as(me, get("/api/v1/feeds").param("tab", "recommended").param("limit", "50"))))
                .contains("followee-PUBLIC", "stranger-PUBLIC")
                .doesNotContain("me-PUBLIC", "me-FOLLOWERS", "me-PRIVATE",
                        "followee-FOLLOWERS", "followee-PRIVATE", "stranger-FOLLOWERS", "stranger-PRIVATE");
    }

    @Test
    @DisplayName("프로필 피드: 본인 전체, 팔로워 PUBLIC과 FOLLOWERS, 그 외와 비로그인 PUBLIC")
    void profileFeed() throws Exception {
        String path = "/api/v1/users/" + followeeName + "/feeds";

        assertThat(contents(as(followee, get(path).param("limit", "50"))))
                .containsExactlyInAnyOrder("followee-PUBLIC", "followee-FOLLOWERS", "followee-PRIVATE");
        assertThat(contents(as(me, get(path).param("limit", "50"))))
                .containsExactlyInAnyOrder("followee-PUBLIC", "followee-FOLLOWERS");
        assertThat(contents(as(stranger, get(path).param("limit", "50"))))
                .containsExactly("followee-PUBLIC");
        assertThat(contents(api(get(path).param("limit", "50"))))
                .containsExactly("followee-PUBLIC");
    }

    @Test
    @DisplayName("피드 지도 팔로잉: 팔로잉 탭과 같은 게시물만 장소에 묶임")
    void feedPlacesFollowing() throws Exception {
        assertThat(placeFeedIds(as(me, get("/api/v1/feeds/places").param("tab", "following"))))
                .containsExactlyInAnyOrderElementsOf(feedIds("me-PUBLIC", "me-FOLLOWERS", "me-PRIVATE",
                        "followee-PUBLIC", "followee-FOLLOWERS"));
    }

    @Test
    @DisplayName("피드 지도 추천: 추천 탭과 같게 다른 사람의 PUBLIC만")
    void feedPlacesRecommended() throws Exception {
        assertThat(placeFeedIds(as(me, get("/api/v1/feeds/places").param("tab", "recommended"))))
                .contains(feedId("followee-PUBLIC"), feedId("stranger-PUBLIC"))
                .doesNotContain(feedId("me-PUBLIC"), feedId("followee-FOLLOWERS"));
    }

    @Test
    @DisplayName("피드 목록과 피드 지도는 로그인 필수 (비로그인 401), 프로필 피드와 게시물 상세만 비로그인 허용")
    void feedListAndMapRequireLogin() throws Exception {
        for (String tab : List.of("following", "recommended")) {
            mvc.perform(api(get("/api/v1/feeds").param("tab", tab).param("limit", "10"))).andExpect(status().isUnauthorized());
            mvc.perform(api(get("/api/v1/feeds/places").param("tab", tab))).andExpect(status().isUnauthorized());
        }
        mvc.perform(api(get("/api/v1/users/" + followeeName + "/feeds").param("limit", "10"))).andExpect(status().isOk());
        mvc.perform(api(get("/api/v1/feeds/" + feedId("followee-PUBLIC")))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("삭제·숨김 게시물은 내 글이어도 네 목록 모두에서 빠짐 (숨김 글은 작성자가 상세로만 볼 수 있음)")
    void deletedAndHiddenAreExcludedEverywhere() throws Exception {
        for (String owner : List.of("me", "followee")) {
            jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", feedId(owner + "-PUBLIC"));
            jdbc.update("UPDATE feeds SET hidden_at = now() WHERE id = ?", feedId(owner + "-FOLLOWERS"));
        }
        jdbc.update("UPDATE feeds SET deleted_at = now() WHERE id = ?", feedId("stranger-PUBLIC"));
        List<String> gone = List.of("me-PUBLIC", "me-FOLLOWERS", "followee-PUBLIC", "followee-FOLLOWERS", "stranger-PUBLIC");

        assertThat(contents(as(me, get("/api/v1/feeds").param("tab", "following").param("limit", "50"))))
                .containsExactlyInAnyOrder("me-PRIVATE")
                .doesNotContainAnyElementsOf(gone);
        assertThat(contents(as(me, get("/api/v1/feeds").param("tab", "recommended").param("limit", "50"))))
                .doesNotContainAnyElementsOf(gone);
        assertThat(contents(as(followee, get("/api/v1/users/" + followeeName + "/feeds").param("limit", "50"))))
                .containsExactly("followee-PRIVATE");
        for (String tab : List.of("following", "recommended")) {
            assertThat(placeFeedIds(as(me, get("/api/v1/feeds/places").param("tab", tab))))
                    .doesNotContainAnyElementsOf(feedIds(gone.toArray(String[]::new)));
        }
    }

    @Test
    @DisplayName("다음 페이지(cursor)에서도 공개 범위 필터가 유지됨: 1개씩 끝까지 넘겨도 결과가 한 번에 받은 것과 같음")
    void filterHoldsAcrossPages() throws Exception {
        assertThat(allPages(me, "following"))
                .containsExactlyInAnyOrder("me-PUBLIC", "me-FOLLOWERS", "me-PRIVATE",
                        "followee-PUBLIC", "followee-FOLLOWERS");
        assertThat(allPages(me, "recommended"))
                .contains("followee-PUBLIC", "stranger-PUBLIC")
                .doesNotContain("me-PUBLIC", "followee-FOLLOWERS", "stranger-FOLLOWERS", "followee-PRIVATE");
    }

    // limit=1로 nextCursor를 따라가며 마지막 페이지까지 모음 (다른 테스트 데이터가 섞이지 않게 이 테스트의 사람 글만)
    private List<String> allPages(long viewer, String tab) throws Exception {
        List<String> collected = new java.util.ArrayList<>();
        Object cursor = null;
        for (int page = 0; page < 500; page++) {
            MockHttpServletRequestBuilder request = get("/api/v1/feeds").param("tab", tab).param("limit", "1");
            if (cursor != null) {
                request.param("cursor", cursor.toString());
            }
            String body = mvc.perform(as(viewer, request)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> contents = JsonPath.read(body, "$.feeds[*].content");
            contents.stream().filter(c -> c.startsWith("me-") || c.startsWith("followee-") || c.startsWith("stranger-"))
                    .forEach(collected::add);
            cursor = JsonPath.read(body, "$.nextCursor");
            if (!Boolean.TRUE.equals(JsonPath.read(body, "$.hasNext"))) {
                return collected;
            }
        }
        throw new AssertionError("페이지가 끝나지 않음");
    }

    private List<String> contents(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.feeds[*].content");
    }

    // 이 테스트가 만든 장소에 묶인 게시물 id만 (다른 테스트 데이터와 섞이지 않게)
    private List<Long> placeFeedIds(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String placePublicId = jdbc.queryForObject("SELECT CAST(public_id AS TEXT) FROM places WHERE id = ?", String.class, place);
        List<List<Number>> groups = JsonPath.read(body, "$[?(@.place.id == '" + placePublicId + "')].feedIds");
        return groups.stream().flatMap(List::stream).map(Number::longValue).toList();
    }

    private List<Long> feedIds(String... contents) {
        return java.util.Arrays.stream(contents).map(this::feedId).toList();
    }

    private long feedId(String content) {
        return jdbc.queryForObject("SELECT id FROM feeds WHERE content = ? AND user_id IN (?, ?, ?)",
                Long.class, content, me, followee, stranger);
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder request) {
        return request.servletPath("/api/v1");
    }

    private static MockHttpServletRequestBuilder as(long userId, MockHttpServletRequestBuilder request) {
        return api(request).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }

    private void insertFeed(long userId, String content, String visibility) {
        long feedId = jdbc.queryForObject("INSERT INTO feeds (user_id, content, visibility) VALUES (?, ?, ?) RETURNING id",
                Long.class, userId, content, visibility);
        jdbc.update("INSERT INTO feed_places (feed_id, place_id) VALUES (?, ?)", feedId, place);
    }

    private long insertUser() {
        String username = "l" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
