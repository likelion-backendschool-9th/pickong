package com.pickong.moderation.adapter.in.web.admin;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.auth.adapter.in.security.ErrorResponseWriter;
import com.pickong.auth.adapter.in.security.LinkAwareAuthorizationRequestResolver;
import com.pickong.auth.adapter.in.security.OAuth2LoginFailureHandler;
import com.pickong.auth.adapter.in.security.OAuth2LoginSuccessHandler;
import com.pickong.auth.adapter.in.security.RestAccessDeniedHandler;
import com.pickong.auth.adapter.in.security.RestAuthenticationEntryPoint;
import com.pickong.auth.adapter.in.security.SecurityConfig;
import com.pickong.auth.adapter.in.security.SignedCookieAuthorizationRequestRepository;
import com.pickong.common.security.AccessTokenVerifier;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.LoginUser;
import com.pickong.common.web.WebMvcConfig;
import com.pickong.moderation.application.port.in.ReportAdminUseCase;
import com.pickong.terms.application.port.in.TermsUseCase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 관리자 신고 API 인가. 실제 보안 필터 체인을 탑니다.
 * 관리자 로그인은 없고, 테스트 주체만 ROLE_ADMIN을 가집니다. 회원 토큰은 ROLE_USER입니다.
 */
@WebMvcTest(controllers = AdminReportController.class)
@Import({
        SecurityConfig.class,
        WebMvcConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ErrorResponseWriter.class
})
@TestPropertySource(properties = {
        "pickong.auth.jwt.secret=01234567890123456789012345678901",
        "pickong.auth.jwt.access-token-ttl=15m",
        "pickong.frontend.base-url=http://localhost:3000"
})
class AdminReportSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReportAdminUseCase reports;

    @MockitoBean
    AccessTokenVerifier accessTokenVerifier;

    @MockitoBean
    SignedCookieAuthorizationRequestRepository authorizationRequestRepository;

    @MockitoBean
    LinkAwareAuthorizationRequestResolver authorizationRequestResolver;

    @MockitoBean
    OAuth2LoginSuccessHandler oauth2SuccessHandler;

    @MockitoBean
    OAuth2LoginFailureHandler oauth2FailureHandler;

    /** SecurityConfig의 재동의 필터가 씁니다. 가짜는 항상 false(동의 받을 약관 없음)라 이 테스트의 인가 결과에 영향이 없습니다. */
    @MockitoBean
    TermsUseCase termsUseCase;

    @Test
    @DisplayName("비로그인 신고 목록은 401")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(api(get("/api/v1/admin/reports").param("status", "PENDING")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(reports, never()).list(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("회원 토큰으로 신고 목록은 403")
    void memberIsForbidden() throws Exception {
        org.mockito.BDDMockito.given(accessTokenVerifier.verify("member-token")).willReturn(Optional.of(new LoginUser(3L)));

        mvc.perform(api(get("/api/v1/admin/reports").param("status", "PENDING"))
                        .cookie(new Cookie(AuthCookies.ACCESS_TOKEN, "member-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(reports, never()).list(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("관리자 기각도 CSRF 토큰이 없으면 403")
    void adminResolveWithoutCsrf() throws Exception {
        mvc.perform(api(post("/api/v1/admin/reports/1/resolve"))
                        .with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISMISS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        verify(reports, never()).resolve(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("회원은 CSRF가 있어도 신고를 처리하지 못한다")
    void memberCannotResolve() throws Exception {
        org.mockito.BDDMockito.given(accessTokenVerifier.verify("member-token")).willReturn(Optional.of(new LoginUser(3L)));

        mvc.perform(api(post("/api/v1/admin/reports/1/resolve"))
                        .with(csrf())
                        .cookie(new Cookie(AuthCookies.ACCESS_TOKEN, "member-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISMISS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private static UsernamePasswordAuthenticationToken admin() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
