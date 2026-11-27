package com.pickong.collection.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import java.util.List;
import java.util.UUID;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /users/{username}/collections: 실제 보안 필터와 표지 계산 SQL(CollectionQueryAdapter)을 함께 확인합니다.
 * 테스트마다 롤백하므로 다른 테스트의 DELETE FROM users를 막지 않습니다. PostGIS 컨테이너가 필요합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class UserCollectionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private String myName;
    private long other;
    private String otherName;

    @BeforeEach
    void setUp() {
        myName = randomUsername();
        me = insertUser(myName, "서연");
        otherName = randomUsername();
        other = insertUser(otherName, null);
    }

    @Test
    @DisplayName("본인: 공개 범위와 관계없이 모두 최신순, 프론트 CollectionDto 모양")
    void mine() throws Exception {
        long publicOne = insertCollection(me, "공개", "PUBLIC", "2026-11-01T00:00:00Z");
        insertCollection(me, "팔로워", "FOLLOWERS", "2026-11-02T00:00:00Z");
        insertCollection(me, "비공개", "PRIVATE", "2026-11-03T00:00:00Z");
        jdbc.update("UPDATE collections SET description = '카페 모음', pin_style = 'GREEN', place_count = 3 WHERE id = ?", publicOne);
        String publicId = jdbc.queryForObject("SELECT CAST(public_id AS TEXT) FROM collections WHERE id = ?", String.class, publicOne);
        String myPublicId = jdbc.queryForObject("SELECT CAST(public_id AS TEXT) FROM users WHERE id = ?", String.class, me);

        list(me, myName)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("비공개", "팔로워", "공개")))
                .andExpect(jsonPath("$[2].id").value(publicId))
                .andExpect(jsonPath("$[2].owner.id").value(myPublicId))
                .andExpect(jsonPath("$[2].owner.username").value(myName))
                .andExpect(jsonPath("$[2].owner.nickname").value("서연"))
                .andExpect(jsonPath("$[2].description").value("카페 모음"))
                .andExpect(jsonPath("$[2].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[2].pinColor").value("GREEN"))
                .andExpect(jsonPath("$[2].placeCount").value(3))
                .andExpect(jsonPath("$[2].coverImageUrl").value(nullValue()))
                .andExpect(jsonPath("$[2].isMine").value(true))
                .andExpect(jsonPath("$[2].price").value(nullValue()))
                .andExpect(jsonPath("$[2].isPurchased").value(false))
                .andExpect(jsonPath("$[2].isLocked").value(false));
    }

    @Test
    @DisplayName("다른 회원: 전체 공개만 (팔로우 테이블이 없어 팔로워 공개도 숨김), isMine=false, 이름이 없으면 nickname은 아이디")
    void othersSeePublicOnly() throws Exception {
        insertCollection(other, "공개", "PUBLIC", "2026-11-01T00:00:00Z");
        insertCollection(other, "팔로워", "FOLLOWERS", "2026-11-02T00:00:00Z");
        insertCollection(other, "비공개", "PRIVATE", "2026-11-03T00:00:00Z");

        list(me, otherName)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("공개")))
                .andExpect(jsonPath("$[0].isMine").value(false))
                .andExpect(jsonPath("$[0].owner.nickname").value(otherName));
    }

    @Test
    @DisplayName("삭제 · 관리자 숨김 컬렉션은 본인에게도 보이지 않는다")
    void hidesDeletedAndHidden() throws Exception {
        insertCollection(me, "남음", "PUBLIC", "2026-11-01T00:00:00Z");
        long deleted = insertCollection(me, "삭제", "PUBLIC", "2026-11-02T00:00:00Z");
        long hidden = insertCollection(me, "숨김", "PUBLIC", "2026-11-03T00:00:00Z");
        jdbc.update("UPDATE collections SET deleted_at = now() WHERE id = ?", deleted);
        jdbc.update("UPDATE collections SET hidden_at = now() WHERE id = ?", hidden);

        list(me, myName).andExpect(jsonPath("$[*].title", contains("남음")));
    }

    @Test
    @DisplayName("표지: 정해 둔 표지가 우선이고, 없으면 순서상 첫 번째로 사진이 있는 장소의 사진 (삭제된 장소는 건너뜀)")
    void coverImage() throws Exception {
        long withCover = insertCollection(me, "표지 있음", "PUBLIC", "2026-11-01T00:00:00Z");
        jdbc.update("UPDATE collections SET cover_image_url = 'https://img/cover.jpg' WHERE id = ?", withCover);
        addPlace(withCover, me, insertPlace("장소", "https://img/place.jpg", "PUBLIC", null), 0);

        long fromPlace = insertCollection(me, "장소 사진", "PUBLIC", "2026-11-02T00:00:00Z");
        long deletedPlace = insertPlace("삭제된 장소", "https://img/deleted.jpg", "PUBLIC", null);
        jdbc.update("UPDATE places SET deleted_at = now() WHERE id = ?", deletedPlace);
        addPlace(fromPlace, me, deletedPlace, 0);
        addPlace(fromPlace, me, insertPlace("사진 없음", null, "PUBLIC", null), 1);
        addPlace(fromPlace, me, insertPlace("두 번째 사진", "https://img/second.jpg", "PUBLIC", null), 3);
        addPlace(fromPlace, me, insertPlace("첫 사진", "https://img/first.jpg", "PUBLIC", null), 2);

        list(me, myName)
                .andExpect(jsonPath("$[0].title").value("장소 사진"))
                .andExpect(jsonPath("$[0].coverImageUrl").value("https://img/first.jpg"))
                .andExpect(jsonPath("$[1].coverImageUrl").value("https://img/cover.jpg"));
    }

    @Test
    @DisplayName("표지: 직접 추가한 비공개 장소의 사진은 소유자에게만 쓴다")
    void coverSkipsPrivatePlaceForOthers() throws Exception {
        long collection = insertCollection(other, "비공개 장소", "PUBLIC", "2026-11-01T00:00:00Z");
        addPlace(collection, other, insertPlace("내가 추가한 곳", "https://img/private.jpg", "PRIVATE", other), 0);
        addPlace(collection, other, insertPlace("공개 장소", "https://img/public.jpg", "PUBLIC", null), 1);

        list(me, otherName).andExpect(jsonPath("$[0].coverImageUrl").value("https://img/public.jpg"));
        list(other, otherName).andExpect(jsonPath("$[0].coverImageUrl").value("https://img/private.jpg"));
    }

    @Test
    @DisplayName("유료 컬렉션은 다른 회원에게 잠김(isLocked), 본인에게는 잠기지 않음")
    void paidIsLockedForOthers() throws Exception {
        long paid = insertCollection(other, "유료", "PUBLIC", "2026-11-01T00:00:00Z");
        jdbc.update("UPDATE collections SET price = 3000 WHERE id = ?", paid);

        list(me, otherName)
                .andExpect(jsonPath("$[0].price").value(3000))
                .andExpect(jsonPath("$[0].isLocked").value(true));
        list(other, otherName).andExpect(jsonPath("$[0].isLocked").value(false));
    }

    @Test
    @DisplayName("컬렉션이 없으면 빈 목록, 아이디는 대소문자를 무시")
    void emptyAndCaseInsensitive() throws Exception {
        list(me, myName.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("없는 아이디는 404 USER_NOT_FOUND")
    void notFound() throws Exception {
        list(me, "nobody_here")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WITHDRAWN_PENDING", "WITHDRAWN", "SUSPENDED", "BANNED"})
    @DisplayName("탈퇴 중 · 탈퇴 · 정지 · 영구 정지 회원의 컬렉션은 프로필과 같이 숨긴다 (404 USER_NOT_FOUND)")
    void hidesInactiveOwners(String userStatus) throws Exception {
        insertCollection(other, "공개", "PUBLIC", "2026-11-01T00:00:00Z");
        jdbc.update("UPDATE users SET status = ? WHERE id = ?", userStatus, other);

        list(me, otherName)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/v1/users/" + myName + "/collections").servletPath("/api/v1"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions list(long viewer, String username) throws Exception {
        return mvc.perform(get("/api/v1/users/" + username + "/collections").servletPath("/api/v1")
                .with(authentication(member(viewer))));
    }

    private static String randomUsername() {
        return "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long insertUser(String username, String displayName) {
        long id = jdbc.queryForObject("INSERT INTO users (username, email, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, username + "@example.com", displayName);
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둡니다.
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private long insertCollection(long ownerId, String title, String visibility, String createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO collections (user_id, title, visibility, created_at, updated_at)
                VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz) RETURNING id
                """, Long.class, ownerId, title, visibility, createdAt, createdAt);
    }

    private long insertPlace(String name, String imageUrl, String visibility, Long ownerId) {
        return jdbc.queryForObject("""
                INSERT INTO places (source_type, name, location, category, visibility, owner_id, image_url)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(127.0, 37.5), 4326)::geography, 'CAFE', ?, ?, ?)
                RETURNING id
                """, Long.class, ownerId == null ? "SYSTEM" : "CUSTOM", name, visibility, ownerId, imageUrl);
    }

    /** 컬렉션 장소는 소유자의 저장 장소에서 담습니다. */
    private void addPlace(long collectionId, long ownerId, long placeId, int sortOrder) {
        long savedPlaceId = jdbc.queryForObject(
                "INSERT INTO saved_places (user_id, place_id) VALUES (?, ?) RETURNING id", Long.class, ownerId, placeId);
        jdbc.update("INSERT INTO collection_places (collection_id, saved_place_id, place_id, sort_order) VALUES (?, ?, ?, ?)",
                collectionId, savedPlaceId, placeId, sortOrder);
    }

    private static UsernamePasswordAuthenticationToken member(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
