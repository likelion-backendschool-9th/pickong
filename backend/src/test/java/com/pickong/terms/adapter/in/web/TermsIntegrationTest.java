package com.pickong.terms.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.application.port.in.TokenUseCase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 약관 전문 · 내 약관 동의 관리 통합 테스트.
 * 실제 PostgreSQL(V2 약관 · V8 조항 시드 포함) · 보안 필터 · 쿠키 · CSRF를 그대로 거칩니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TermsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenUseCase tokenUseCase;

    private long me;
    private Cookie access;

    /** 가입한 회원 한 명: 필수 약관 두 개(1.0)에만 동의한 상태 */
    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
        me = jdbc.queryForObject("INSERT INTO users (username) VALUES ('seoyeon') RETURNING id", Long.class);
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, TIMESTAMPTZ '2026-11-20 10:00:00+09' FROM terms WHERE type IN ('SERVICE', 'PRIVACY')
                """, me);
        access = new Cookie(AuthCookies.ACCESS_TOKEN, tokenUseCase.issue(me).accessToken());
    }

    private ResultActions patchAgreement(String json) throws Exception {
        return mvc.perform(patch("/api/v1/me/agreements").servletPath("/api/v1").with(csrf()).cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 마케팅 동의 기록 (철회한 것 포함): [agreed_at, revoked_at] */
    private List<Map<String, Object>> marketingRows() {
        return jdbc.queryForList("""
                SELECT a.agreed_at, a.revoked_at FROM user_terms_agreements a JOIN terms t ON t.id = a.terms_id
                WHERE a.user_id = ? AND t.type = 'MARKETING' ORDER BY a.id
                """, me);
    }

    // ── 약관 전문 ───────────────────────────────────────────

    @Test
    @DisplayName("GET /terms/service: 로그인 없이 200, 프론트 Terms 모양, V8 조항이 순서대로, 시행일은 한국 날짜")
    void termsWithoutLogin() throws Exception {
        mvc.perform(get("/api/v1/terms/service").servletPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SERVICE"))
                .andExpect(jsonPath("$.title").value("서비스 이용약관"))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.version").value("1.0"))
                .andExpect(jsonPath("$.effectiveDate").value("2026-11-01"))
                .andExpect(jsonPath("$.sections.length()").value(4))
                .andExpect(jsonPath("$.sections[0].heading").value("제1조 (목적)"))
                .andExpect(jsonPath("$.sections[3].heading").value("제4조 (탈퇴)"));
    }

    @Test
    @DisplayName("GET /terms/MARKETING: 대문자도 된다 · 선택 약관")
    void termsUpperCase() throws Exception {
        mvc.perform(get("/api/v1/terms/MARKETING").servletPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(false))
                .andExpect(jsonPath("$.sections.length()").value(2));
    }

    @Test
    @DisplayName("GET /terms/location: 없는 종류는 404 TERMS_NOT_FOUND")
    void unknownTerms() throws Exception {
        mvc.perform(get("/api/v1/terms/location").servletPath("/api/v1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_FOUND"));
    }

    // ── 내 동의 상태 ────────────────────────────────────────

    @Test
    @DisplayName("GET /me/agreements: 세 줄(SERVICE · PRIVACY · MARKETING), 필수 두 개만 동의함")
    void myAgreements() throws Exception {
        mvc.perform(get("/api/v1/me/agreements").servletPath("/api/v1").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("SERVICE"))
                .andExpect(jsonPath("$[0].agreed").value(true))
                .andExpect(jsonPath("$[0].version").value("1.0"))
                .andExpect(jsonPath("$[0].agreedAt").exists())
                .andExpect(jsonPath("$[1].type").value("PRIVACY"))
                .andExpect(jsonPath("$[2].type").value("MARKETING"))
                .andExpect(jsonPath("$[2].required").value(false))
                .andExpect(jsonPath("$[2].agreed").value(false))
                .andExpect(jsonPath("$[2].agreedAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /me/agreements: 로그인하지 않으면 401")
    void agreementsNeedLogin() throws Exception {
        mvc.perform(get("/api/v1/me/agreements").servletPath("/api/v1"))
                .andExpect(status().isUnauthorized());
    }

    // ── 동의 · 철회 ─────────────────────────────────────────

    @Test
    @DisplayName("PATCH 마케팅 동의 → 철회 → 다시 동의: 응답에 반영되고, 철회한 기록은 지우지 않고 이력으로 남는다")
    void agreeRevokeAgree() throws Exception {
        patchAgreement("{\"type\":\"MARKETING\",\"agreed\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].agreed").value(true))
                .andExpect(jsonPath("$[2].agreedAt").exists());

        patchAgreement("{\"type\":\"MARKETING\",\"agreed\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].agreed").value(false));

        patchAgreement("{\"type\":\"MARKETING\",\"agreed\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].agreed").value(true));

        List<Map<String, Object>> rows = marketingRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("revoked_at")).isNotNull();
        assertThat(rows.get(1).get("revoked_at")).isNull();
    }

    @Test
    @DisplayName("PATCH 같은 동의를 두 번 보내도 기록은 하나")
    void agreeIdempotent() throws Exception {
        patchAgreement("{\"type\":\"marketing\",\"agreed\":true}").andExpect(status().isOk());
        patchAgreement("{\"type\":\"MARKETING\",\"agreed\":true}").andExpect(status().isOk());

        assertThat(marketingRows()).hasSize(1);
    }

    @Test
    @DisplayName("PATCH 필수 약관 철회는 400 REQUIRED_TERMS_CANNOT_BE_REVOKED이고 동의는 그대로")
    void requiredCannotRevoke() throws Exception {
        patchAgreement("{\"type\":\"PRIVACY\",\"agreed\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_TERMS_CANNOT_BE_REVOKED"));

        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_terms_agreements a JOIN terms t ON t.id = a.terms_id
                WHERE a.user_id = ? AND t.type = 'PRIVACY' AND a.revoked_at IS NULL
                """, Integer.class, me);
        assertThat(active).isEqualTo(1);
    }

    @Test
    @DisplayName("PATCH 잘못된 입력: 모르는 종류 400 UNKNOWN_TERMS · agreed 없음 400 INVALID_INPUT")
    void invalidInput() throws Exception {
        patchAgreement("{\"type\":\"LOCATION\",\"agreed\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_TERMS"));
        patchAgreement("{\"type\":\"MARKETING\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("PATCH CSRF 헤더가 없으면 403 INVALID_CSRF_TOKEN이고 기록하지 않는다")
    void needsCsrf() throws Exception {
        mvc.perform(patch("/api/v1/me/agreements").servletPath("/api/v1").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"MARKETING\",\"agreed\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        assertThat(marketingRows()).isEmpty();
    }
}
