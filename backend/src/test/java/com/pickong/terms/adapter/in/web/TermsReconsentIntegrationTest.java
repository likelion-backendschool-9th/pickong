package com.pickong.terms.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.application.port.in.TokenUseCase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 새 버전 재동의 통합 테스트. 실제 PostgreSQL · 보안 필터(재동의 필터 포함) · 쿠키 · CSRF를 그대로 거칩니다.
 * <p>
 * 테스트마다 '재동의 필요' 새 버전 행을 직접 넣고, 끝나면 지웁니다. 지우지 않으면 같은 DB를 쓰는 다른 테스트에서
 * 시행 중인 약관이 바뀌어 버립니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TermsReconsentIntegrationTest {

    /** 테스트가 넣는 새 버전. 지울 때 이 값으로 찾습니다 */
    private static final String TEST_VERSION = "9.9-test";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenUseCase tokenUseCase;

    private long me;
    private Cookie access;

    /** 1.0 세 개에 모두 동의한 회원 한 명 */
    @BeforeEach
    void setUp() {
        clean();
        me = jdbc.queryForObject("INSERT INTO users (username) VALUES ('seoyeon') RETURNING id", Long.class);
        jdbc.update("""
                INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
                SELECT ?, id, TIMESTAMPTZ '2026-11-20 10:00:00+09' FROM terms WHERE version = '1.0'
                """, me);
        access = new Cookie(AuthCookies.ACCESS_TOKEN, tokenUseCase.issue(me).accessToken());
    }

    /** 이 테스트는 실제로 커밋하므로, 넣은 회원 · 동의 · 새 약관 버전을 지워 다른 테스트에 남기지 않습니다. */
    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM terms WHERE version = ?", TEST_VERSION);
    }

    /** 어제 시행된 새 버전 한 행을 넣습니다 */
    private void newVersion(String type, boolean required, boolean requiresReconsent) {
        jdbc.update("""
                INSERT INTO terms (type, version, title, content_url, is_required, effective_at, requires_reconsent)
                VALUES (?, ?, ?, ?, ?, now() - interval '1 day', ?)
                """, type, TEST_VERSION, type + " 새 버전", "/terms/" + type.toLowerCase(), required, requiresReconsent);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.servletPath("/api/v1").cookie(access));
    }

    private ResultActions agree(String json) throws Exception {
        return perform(post("/api/v1/me/agreements").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("재동의 필요 없는 새 버전: 아무것도 막지 않고 동의 받을 약관도 없다")
    void newVersionWithoutReconsentBlocksNothing() throws Exception {
        newVersion("PRIVACY", true, false);

        perform(get("/api/v1/me/social-accounts")).andExpect(status().isOk());
        perform(get("/api/v1/me/agreements/pending")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        perform(get("/api/v1/me/agreements"))
                .andExpect(jsonPath("$[1].agreed").value(true))
                .andExpect(jsonPath("$[1].version").value("1.0"));
    }

    @Test
    @DisplayName("필수 약관 재동의 필요: 다른 API는 403 TERMS_RECONSENT_REQUIRED, 동의 · 내 정보 · 약관 조회는 열려 있다")
    void requiredReconsentBlocksOtherApis() throws Exception {
        newVersion("PRIVACY", true, true);

        perform(get("/api/v1/me/social-accounts"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_RECONSENT_REQUIRED"));
        perform(get("/api/v1/me")).andExpect(status().isOk());
        perform(get("/api/v1/terms/privacy")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(TEST_VERSION));
        perform(get("/api/v1/me/agreements")).andExpect(status().isOk())
                .andExpect(jsonPath("$[1].agreed").value(false))
                .andExpect(jsonPath("$[1].version").value(TEST_VERSION));
        perform(get("/api/v1/me/agreements/pending")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PRIVACY"))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].version").value(TEST_VERSION));
    }

    @Test
    @DisplayName("재동의: 필수 약관이 빠지면 400이고 계속 막힌다 → 동의하면 시행 버전으로 기록되고 바로 풀린다")
    void agreeUnblocks() throws Exception {
        newVersion("PRIVACY", true, true);

        agree("{\"agreedTerms\":[\"SERVICE\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_TERMS_NOT_AGREED"));
        perform(get("/api/v1/me/social-accounts")).andExpect(status().isForbidden());

        agree("{\"agreedTerms\":[\"SERVICE\",\"PRIVACY\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].agreed").value(true))
                .andExpect(jsonPath("$[1].version").value(TEST_VERSION));
        perform(get("/api/v1/me/social-accounts")).andExpect(status().isOk());

        Integer newRecords = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_terms_agreements a JOIN terms t ON t.id = a.terms_id
                WHERE a.user_id = ? AND t.version = ? AND a.revoked_at IS NULL
                """, Integer.class, me, TEST_VERSION);
        assertThat(newRecords).as("새 버전 기록은 개인정보 하나 (서비스 약관은 1.0 동의가 유효해서 다시 기록하지 않음)").isEqualTo(1);
    }

    @Test
    @DisplayName("동의 전에도 CSRF는 그대로: POST /me/agreements에 CSRF 헤더가 없으면 403 INVALID_CSRF_TOKEN")
    void agreeNeedsCsrf() throws Exception {
        newVersion("PRIVACY", true, true);

        perform(post("/api/v1/me/agreements").contentType(MediaType.APPLICATION_JSON).content("{\"agreedTerms\":[\"PRIVACY\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
    }

    @Test
    @DisplayName("선택 약관 재동의 필요: 서비스는 막지 않고, 다시 묻는 약관으로 나온다. 거절하면 예전 동의가 철회돼 다시 묻지 않는다")
    void optionalReconsentDecline() throws Exception {
        newVersion("MARKETING", false, true);

        perform(get("/api/v1/me/social-accounts")).andExpect(status().isOk());
        perform(get("/api/v1/me/agreements/pending"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("MARKETING"))
                .andExpect(jsonPath("$[0].required").value(false));

        agree("{\"agreedTerms\":[]}").andExpect(status().isOk()).andExpect(jsonPath("$[2].agreed").value(false));

        perform(get("/api/v1/me/agreements/pending")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("설정에서 마케팅을 켜면 재동의 필요 새 버전으로 기록된다 (#89 리뷰 4번)")
    void settingsSwitchRecordsNewVersion() throws Exception {
        newVersion("MARKETING", false, true);

        perform(patch("/api/v1/me/agreements").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MARKETING\",\"agreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].agreed").value(true))
                .andExpect(jsonPath("$[2].version").value(TEST_VERSION));
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 재동의 필터와 관계없이 원래대로 401")
    void anonymousStillUnauthorized() throws Exception {
        newVersion("PRIVACY", true, true);

        mvc.perform(get("/api/v1/me/social-accounts").servletPath("/api/v1"))
                .andExpect(status().isUnauthorized());
    }
}
