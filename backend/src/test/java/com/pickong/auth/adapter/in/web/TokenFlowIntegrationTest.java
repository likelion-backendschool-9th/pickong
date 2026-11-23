package com.pickong.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.application.dto.IssuedTokens;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.auth.application.port.out.RefreshTokenPersistencePort;
import com.pickong.auth.domain.model.RefreshToken;
import com.pickong.auth.domain.vo.RawRefreshToken;
import com.pickong.common.security.AccessTokenVerifier;
import com.pickong.common.security.LoginUser;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 재발급 · 로그아웃 통합 테스트. 실제 PostgreSQL · 보안 필터 · 쿠키를 그대로 거칩니다.
 * 로그인 API는 4단계에서 생기므로, 세션은 {@link TokenUseCase#issue}로 직접 만들어 씁니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TokenFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TokenUseCase tokenUseCase;

    @Autowired
    RefreshTokenPersistencePort refreshTokens;

    @Autowired
    AccessTokenVerifier accessTokenVerifier;

    @Autowired
    JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void setUp() {
        // 테스트마다 깨끗한 상태에서 시작합니다. (요청 처리 트랜잭션을 실제처럼 커밋해야 해서 롤백 방식을 쓰지 않음)
        // 외래 키 순서대로 지웁니다. (같은 DB를 쓰는 다른 테스트 클래스와 섞이지 않게)
        jdbc.update("DELETE FROM user_terms_agreements");
        jdbc.update("DELETE FROM user_social_accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
        userId = jdbc.queryForObject("INSERT INTO users (username) VALUES ('seoyeon') RETURNING id", Long.class);
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private MvcResult refresh(String rawRefreshToken) throws Exception {
        MockHttpServletRequestBuilder request = api(post("/api/v1/auth/refresh")).with(csrf());
        if (rawRefreshToken != null) {
            request.cookie(new Cookie(AuthCookies.REFRESH_TOKEN, rawRefreshToken));
        }
        return mvc.perform(request).andReturn();
    }

    private RefreshToken stored(String rawRefreshToken) {
        return refreshTokens.findByTokenHash(new RawRefreshToken(rawRefreshToken).hash()).orElseThrow();
    }

    private List<String> familyRevokedAt(String family) {
        return jdbc.queryForList("SELECT CAST(revoked_at AS TEXT) FROM refresh_tokens WHERE family = ?", String.class, family);
    }

    /** 응답의 Set-Cookie 중 이름이 같은 것 */
    private static String setCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿠키가 응답에 없습니다"));
    }

    private static String cookieValue(String setCookieHeader) {
        return setCookieHeader.substring(setCookieHeader.indexOf('=') + 1, setCookieHeader.indexOf(';'));
    }

    private static void assertCookiesCleared(MvcResult result) {
        assertThat(setCookie(result, AuthCookies.ACCESS_TOKEN)).contains("Max-Age=0");
        assertThat(setCookie(result, AuthCookies.REFRESH_TOKEN)).contains("Max-Age=0").contains("Path=/api/v1/auth");
    }

    @Test
    @DisplayName("재발급 성공: 204 + 새 쿠키 두 개, 쓴 토큰은 폐기되고 같은 family로 새 토큰이 생긴다")
    void refreshRotates() throws Exception {
        IssuedTokens issued = tokenUseCase.issue(userId);
        String family = stored(issued.refreshToken()).getFamily();

        MvcResult result = refresh(issued.refreshToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        String accessCookie = setCookie(result, AuthCookies.ACCESS_TOKEN);
        String refreshCookie = setCookie(result, AuthCookies.REFRESH_TOKEN);
        assertThat(accessCookie).contains("Path=/;", "Max-Age=900", "HttpOnly", "SameSite=Lax", "Secure");
        assertThat(refreshCookie).contains("Path=/api/v1/auth", "Max-Age=1209600", "HttpOnly", "SameSite=Lax", "Secure");

        String newRefresh = cookieValue(refreshCookie);
        assertThat(newRefresh).isNotEqualTo(issued.refreshToken());
        assertThat(stored(issued.refreshToken()).getRevokedAt()).as("쓴 토큰은 폐기").isNotNull();
        RefreshToken next = stored(newRefresh);
        assertThat(next.getFamily()).isEqualTo(family);
        assertThat(next.getRevokedAt()).isNull();
        assertThat(next.getExpiresAt()).isAfter(OffsetDateTime.now().plusDays(13));
        assertThat(accessTokenVerifier.verify(cookieValue(accessCookie))).contains(new LoginUser(userId));
    }

    @Test
    @DisplayName("이미 바뀐 토큰이 다시 오면 401 REFRESH_TOKEN_REUSED, 쿠키 삭제, 같은 family 전체 폐기")
    void reuseRevokesFamily() throws Exception {
        IssuedTokens issued = tokenUseCase.issue(userId);
        String family = stored(issued.refreshToken()).getFamily();
        String rotated = cookieValue(setCookie(refresh(issued.refreshToken()), AuthCookies.REFRESH_TOKEN));

        MvcResult reused = refresh(issued.refreshToken());

        assertThat(reused.getResponse().getStatus()).isEqualTo(401);
        assertThat(reused.getResponse().getContentAsString()).contains("\"code\":\"REFRESH_TOKEN_REUSED\"");
        assertCookiesCleared(reused);
        assertThat(familyRevokedAt(family)).as("family의 모든 토큰이 폐기").doesNotContainNull();

        // 정상 사용자가 갖고 있던 최신 토큰도 함께 폐기되어, 공격자와 사용자 모두 다시 로그인해야 합니다.
        MvcResult afterReuse = refresh(rotated);
        assertThat(afterReuse.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("만료된 토큰은 401 INVALID_REFRESH_TOKEN, 쿠키 삭제")
    void expiredToken() throws Exception {
        RawRefreshToken raw = RawRefreshToken.generate();
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, family, expires_at) VALUES (?, ?, 'family-x', now() - interval '1 minute')",
                userId, raw.hash());

        MvcResult result = refresh(raw.value());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"INVALID_REFRESH_TOKEN\"");
        assertCookiesCleared(result);
    }

    @Test
    @DisplayName("쿠키가 없거나 DB에 없는 토큰이면 401 INVALID_REFRESH_TOKEN")
    void missingOrUnknownToken() throws Exception {
        assertThat(refresh(null).getResponse().getStatus()).isEqualTo(401);
        MvcResult unknown = refresh(RawRefreshToken.generate().value());
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getContentAsString()).contains("\"code\":\"INVALID_REFRESH_TOKEN\"");
    }

    @Test
    @DisplayName("정지된 회원은 재발급할 수 없고(401 ACCOUNT_UNAVAILABLE) 세션이 폐기된다")
    void suspendedUserCannotRefresh() throws Exception {
        IssuedTokens issued = tokenUseCase.issue(userId);
        String family = stored(issued.refreshToken()).getFamily();
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", userId);

        MvcResult result = refresh(issued.refreshToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"ACCOUNT_UNAVAILABLE\"");
        assertCookiesCleared(result);
        assertThat(familyRevokedAt(family)).doesNotContainNull();
    }

    @Test
    @DisplayName("로그아웃: 204 + 쿠키 삭제, 이 기기의 세션만 폐기되고 다른 기기는 그대로")
    void logoutRevokesOnlyThisDevice() throws Exception {
        IssuedTokens phone = tokenUseCase.issue(userId);
        IssuedTokens laptop = tokenUseCase.issue(userId);

        MvcResult result = mvc.perform(api(post("/api/v1/auth/logout")).with(csrf())
                        .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, phone.refreshToken())))
                .andExpect(status().isNoContent())
                .andReturn();

        assertCookiesCleared(result);
        assertThat(refresh(phone.refreshToken()).getResponse().getStatus()).as("로그아웃한 기기").isEqualTo(401);
        assertThat(refresh(laptop.refreshToken()).getResponse().getStatus()).as("다른 기기").isEqualTo(204);
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 204 (여러 번 눌러도 안전)")
    void logoutWithoutCookie() throws Exception {
        mvc.perform(api(post("/api/v1/auth/logout")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("재발급도 CSRF 헤더가 없으면 403 INVALID_CSRF_TOKEN")
    void refreshRequiresCsrf() throws Exception {
        IssuedTokens issued = tokenUseCase.issue(userId);

        mvc.perform(api(post("/api/v1/auth/refresh")).cookie(new Cookie(AuthCookies.REFRESH_TOKEN, issued.refreshToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
    }

    @Test
    @DisplayName("같은 토큰을 동시에 폐기하려 해도 한 번만 성공한다 (동시 재발급 시 둘 다 새 토큰을 받지 못함)")
    void revokeIfActiveOnlyOnce() {
        IssuedTokens issued = tokenUseCase.issue(userId);
        Long tokenId = stored(issued.refreshToken()).getId();
        OffsetDateTime now = OffsetDateTime.now();

        assertThat(refreshTokens.revokeIfActive(tokenId, now)).isTrue();
        assertThat(refreshTokens.revokeIfActive(tokenId, now)).isFalse();
    }
}
