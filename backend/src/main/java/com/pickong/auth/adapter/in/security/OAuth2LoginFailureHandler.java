package com.pickong.auth.adapter.in.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인이 실패하면(콜백 단계) 프론트 로그인 화면으로 사유와 함께 보냅니다.
 * <ul>
 *   <li>동의 화면에서 취소(access_denied) → /login?error=OAUTH_CANCELLED</li>
 *   <li>state 불일치 · 만료(로그인 CSRF 의심 포함), 토큰 교환 실패, 제공자 오류 → /login?error=OAUTH_FAILED</li>
 * </ul>
 * 원인은 서버 로그에만 남기고 화면에는 드러내지 않습니다.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final FrontendProperties frontend;

    public OAuth2LoginFailureHandler(FrontendProperties frontend) {
        this.frontend = frontend;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String errorCode = exception instanceof OAuth2AuthenticationException oauth ? oauth.getError().getErrorCode() : "unknown";
        String reason = "access_denied".equals(errorCode) ? "OAUTH_CANCELLED" : "OAUTH_FAILED";
        log.info("소셜 로그인 실패: {} ({})", errorCode, reason);
        response.sendRedirect(frontend.url("/login?error=" + reason));
    }
}
