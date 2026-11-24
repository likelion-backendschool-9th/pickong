package com.pickong.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import com.pickong.auth.application.port.out.TokenIssuerPort;
import com.pickong.common.error.BusinessException;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.feed.domain.FeedErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보안 필터 체인 통합 테스트. 실제 설정(SecurityConfig)과 쿠키 · CSRF 흐름을 그대로 거칩니다.
 * 로그인이 필요한 실제 API가 아직 없어서, 테스트 전용 컨트롤러(/api/v1/test-security/**)로 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SecurityIntegrationTest.TestSecurityController.class})
class SecurityIntegrationTest {

    @RestController
    static class TestSecurityController {

        @GetMapping("/test-security/me")
        Long me(@CurrentUser LoginUser user) {
            return user.userId();
        }

        @PostMapping("/test-security/me")
        Long changeSomething(@CurrentUser LoginUser user) {
            return user.userId();
        }

        @GetMapping("/test-security/forbidden")
        void forbidden() {
            throw new AccessDeniedException("다른 사람의 리소스");
        }

        @GetMapping("/test-security/business-error")
        void businessError() {
            throw new BusinessException(FeedErrorCode.FEED_NOT_FOUND);
        }

        record NameRequest(@NotBlank(message = "이름을 입력해 주세요.") String name) {
        }

        @PostMapping("/test-security/validation")
        String validation(@RequestBody @Valid NameRequest request) {
            return request.name();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    TokenIssuerPort tokenIssuer;

    /** DispatcherServlet이 /api/v1에 매핑되어 있어 실제 요청처럼 servletPath를 지정합니다. */
    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private Cookie accessCookie(long userId) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenIssuer.issueAccessToken(userId));
    }

    @Test
    @DisplayName("로그인이 필요한 경로에 쿠키 없이 오면 401 UNAUTHORIZED (공통 오류 형식)")
    void unauthenticated() throws Exception {
        mvc.perform(api(get("/api/v1/test-security/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("유효한 Access Token 쿠키가 있으면 @CurrentUser로 회원 id를 받는다")
    void authenticatedWithCookie() throws Exception {
        mvc.perform(api(get("/api/v1/test-security/me")).cookie(accessCookie(42L)))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    @DisplayName("위조 · 만료된 토큰은 로그인이 필요한 경로에서 401")
    void invalidTokenOnProtectedPath() throws Exception {
        mvc.perform(api(get("/api/v1/test-security/me")).cookie(new Cookie(AuthCookies.ACCESS_TOKEN, "broken.token.value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("공개 경로는 만료 · 위조된 쿠키가 남아 있어도 401을 내지 않는다")
    void invalidTokenOnPublicPath() throws Exception {
        mvc.perform(api(get("/api/v1/actuator/health")).cookie(new Cookie(AuthCookies.ACCESS_TOKEN, "broken.token.value")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인했지만 권한이 없으면 403 FORBIDDEN")
    void forbidden() throws Exception {
        mvc.perform(api(get("/api/v1/test-security/forbidden")).cookie(accessCookie(42L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("상태를 바꾸는 요청에 CSRF 토큰이 없으면 403 INVALID_CSRF_TOKEN")
    void postWithoutCsrfToken() throws Exception {
        mvc.perform(api(post("/api/v1/test-security/me")).cookie(accessCookie(42L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
    }

    @Test
    @DisplayName("GET 응답의 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내면 POST가 통과한다 (프론트와 같은 흐름)")
    void postWithCsrfDoubleSubmit() throws Exception {
        MvcResult first = mvc.perform(api(get("/api/v1/test-security/me")).cookie(accessCookie(42L)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie xsrf = first.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).as("GET 응답에 XSRF-TOKEN 쿠키가 있어야 프론트가 다음 요청에 헤더로 보낼 수 있습니다").isNotNull();
        assertThat(xsrf.isHttpOnly()).as("프론트 JS가 읽어야 하므로 HttpOnly가 아니어야 합니다").isFalse();
        assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Lax");

        mvc.perform(api(post("/api/v1/test-security/me"))
                        .cookie(accessCookie(42L), xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    @DisplayName("모듈 오류 코드(BusinessException)는 {status, code, message}로 응답한다")
    void businessErrorFormat() throws Exception {
        mvc.perform(api(get("/api/v1/test-security/business-error")).cookie(accessCookie(42L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("FEED_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("피드를 찾을 수 없습니다"));
    }

    @Test
    @DisplayName("입력 검증 실패는 400 INVALID_INPUT, 메시지는 필드 오류 문구")
    void validationErrorFormat() throws Exception {
        mvc.perform(api(post("/api/v1/test-security/validation"))
                        .cookie(accessCookie(42L))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("이름을 입력해 주세요."));
    }

    @Test
    @DisplayName("세션을 만들지 않는다 (STATELESS)")
    void noSession() throws Exception {
        MvcResult result = mvc.perform(api(get("/api/v1/test-security/me")).cookie(accessCookie(42L))).andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
