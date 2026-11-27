package com.pickong.savedplace.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.common.security.LoginUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /saved-places: 실제 보안 필터와 places JOIN SQL(SavedPlaceQueryAdapter)을 함께 확인합니다.
 * 개인 데이터라 본인 것만, 지금 볼 수 있는 장소만 나오는지가 핵심입니다. PostGIS 컨테이너가 필요합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SavedPlaceControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("비로그인은 401")
    void requiresLogin() throws Exception {
        mvc.perform(api(get("/api/v1/saved-places")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("저장한 장소가 없으면 빈 목록")
    void emptyList() throws Exception {
        long me = insertUser();

        mvc.perform(mine(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("본인이 저장한 장소만 최근 저장순으로 돌려주고, 다른 회원의 저장은 섞이지 않는다")
    void onlyMine() throws Exception {
        long me = insertUser();
        long other = insertUser();
        long older = insertPlace("오래된 저장", "PUBLIC", null);
        long newer = insertPlace("최근 저장", "PUBLIC", null);
        long othersOnly = insertPlace("남이 저장", "PUBLIC", null);
        save(me, older, "2026-11-01T00:00:00Z");
        save(me, newer, "2026-11-02T00:00:00Z");
        save(other, othersOnly, "2026-11-03T00:00:00Z");
        save(other, older, "2026-11-03T00:00:00Z");

        mvc.perform(mine(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].place.name", contains("최근 저장", "오래된 저장")))
                .andExpect(jsonPath("$[0].place.lat").value(37.5))
                .andExpect(jsonPath("$[0].place.lng").value(127.0))
                .andExpect(jsonPath("$[0].savedAt").exists());
    }

    @Test
    @DisplayName("삭제된 장소와 남의 비공개 장소는 빼고, 내 비공개 장소는 보여 준다")
    void visibility() throws Exception {
        long me = insertUser();
        long other = insertUser();
        long mine = insertPlace("내 비공개", "PRIVATE", me);
        long othersPrivate = insertPlace("남의 비공개", "PRIVATE", other);
        long deleted = insertPlace("삭제된 장소", "PUBLIC", null);
        long visible = insertPlace("공개 장소", "PUBLIC", null);
        jdbc.update("UPDATE places SET deleted_at = now() WHERE id = ?", deleted);
        for (long place : new long[] {mine, othersPrivate, deleted, visible}) {
            save(me, place, "2026-11-01T00:00:00Z");
        }

        mvc.perform(mine(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].place.name", containsInAnyOrder("내 비공개", "공개 장소")));
    }

    @Test
    @DisplayName("같은 회원은 같은 장소를 두 번 저장할 수 없다")
    void uniquePerUser() {
        long me = insertUser();
        long place = insertPlace("한 번만", "PUBLIC", null);
        save(me, place, "2026-11-01T00:00:00Z");

        assertThatThrownBy(() -> save(me, place, "2026-11-02T00:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("저장된 장소는 하드 삭제할 수 없다 (장소는 soft delete)")
    void placeDeleteRestricted() {
        long me = insertUser();
        long place = insertPlace("삭제 금지", "PUBLIC", null);
        save(me, place, "2026-11-01T00:00:00Z");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM places WHERE id = ?", place))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private MockHttpServletRequestBuilder mine(long userId) {
        return api(get("/api/v1/saved-places")).with(authentication(member(userId)));
    }

    private long insertUser() {
        String username = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long id = jdbc.queryForObject(
                "INSERT INTO users (username, email) VALUES (?, ?) RETURNING id", Long.class, username, username + "@example.com");
        // 필수 약관 재동의 필터에 걸리지 않도록 동의해 둡니다.
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, now() FROM terms WHERE is_required = TRUE
                """, id);
        return id;
    }

    private long insertPlace(String name, String visibility, Long ownerId) {
        return jdbc.queryForObject("""
                INSERT INTO places (source_type, name, location, category, visibility, owner_id)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(127.0, 37.5), 4326)::geography, 'CAFE', ?, ?)
                RETURNING id
                """, Long.class, ownerId == null ? "SYSTEM" : "CUSTOM", name, visibility, ownerId);
    }

    private void save(long userId, long placeId, String savedAt) {
        jdbc.update("INSERT INTO saved_places (user_id, place_id, source_type, created_at) VALUES (?, ?, 'SEARCH', ?::timestamptz)",
                userId, placeId, savedAt);
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private static UsernamePasswordAuthenticationToken member(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
