package com.pickong.moderation.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import com.pickong.common.security.AdminAccessVerifier;
import com.pickong.user.application.port.in.UserAccountUseCase;
import com.pickong.common.security.LoginUser;
import com.pickong.common.web.WebMvcConfig;
import com.pickong.moderation.application.dto.ReportReceipt;
import com.pickong.moderation.application.port.in.ReportUseCase;
import com.pickong.terms.application.port.in.TermsUseCase;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 회원 신고 API 인가. 실제 보안 필터 체인을 탑니다. */
@WebMvcTest(controllers = ReportController.class)
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
class ReportControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReportUseCase reports;

    @MockitoBean
    AccessTokenVerifier accessTokenVerifier;

    @MockitoBean
    UserAccountUseCase accounts;

    @MockitoBean
    AdminAccessVerifier admins;

    @BeforeEach
    void activeTestPrincipals() {
        org.mockito.BDDMockito.given(accounts.canLogin(org.mockito.ArgumentMatchers.anyLong())).willReturn(true);
        org.mockito.BDDMockito.given(admins.isActive(org.mockito.ArgumentMatchers.anyLong())).willReturn(true);
    }

    @MockitoBean
    SignedCookieAuthorizationRequestRepository authorizationRequestRepository;

    @MockitoBean
    LinkAwareAuthorizationRequestResolver authorizationRequestResolver;

    @MockitoBean
    OAuth2LoginSuccessHandler oauth2SuccessHandler;

    @MockitoBean
    OAuth2LoginFailureHandler oauth2FailureHandler;

    @MockitoBean
    TermsUseCase termsUseCase;

    @Test
    @DisplayName("비로그인 신고 접수는 401")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(api(post("/api/v1/reports")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(reports, never()).report(any());
    }

    @Test
    @DisplayName("회원 신고도 CSRF 토큰이 없으면 403")
    void memberReportWithoutCsrf() throws Exception {
        org.mockito.BDDMockito.given(accessTokenVerifier.verify("member-token")).willReturn(Optional.of(new LoginUser(3L)));

        mvc.perform(api(post("/api/v1/reports"))
                        .cookie(new Cookie(AuthCookies.ACCESS_TOKEN, "member-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        verify(reports, never()).report(any());
    }

    @Test
    @DisplayName("로그인한 회원은 CSRF가 있으면 신고를 접수한다")
    void memberReportWithCsrf() throws Exception {
        org.mockito.BDDMockito.given(accessTokenVerifier.verify("member-token")).willReturn(Optional.of(new LoginUser(3L)));
        org.mockito.BDDMockito.given(reports.report(any())).willReturn(new ReportReceipt(
                5L, "FEED", 41L, "SPAM", "PENDING", OffsetDateTime.parse("2026-11-25T03:00:00Z")));

        mvc.perform(api(post("/api/v1/reports"))
                        .with(csrf())
                        .cookie(new Cookie(AuthCookies.ACCESS_TOKEN, "member-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("POST"))
                .andExpect(jsonPath("$.snapshot").doesNotExist());
        verify(reports).report(any());
    }

    private static String body() {
        return "{\"targetType\":\"POST\",\"targetId\":41,\"reason\":\"SPAM\"}";
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }
}
