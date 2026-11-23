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
 * 소셜 로그인 · 계정 연결이 실패하면 프론트 화면으로 사유와 함께 보냅니다.
 * 로그인이면 /login?error=사유, 계정 연결이면 /settings/login-methods?error=사유.
 * <ul>
 *   <li>연결 시작 거절(연결 토큰 만료 · 위조, 로그인한 회원과 다름) → LINK_EXPIRED</li>
 *   <li>동의 화면에서 취소(access_denied) → OAUTH_CANCELLED</li>
 *   <li>state 불일치 · 만료(로그인 CSRF 의심 포함), 토큰 교환 실패, 제공자 오류 → OAUTH_FAILED</li>
 * </ul>
 * 시작 단계(동의 화면으로 보내기 전)와 콜백 단계 둘 다 이 처리기를 씁니다. (SecurityConfig에서 연결)
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
        LinkStartRejectedException linkRejected = findCause(exception, LinkStartRejectedException.class);
        if (linkRejected != null) {
            log.info("계정 연결 시작 거절: {}", linkRejected.getMessage());
            response.sendRedirect(frontend.url(OAuth2LinkRequests.failurePath(OAuth2LinkRequests.LINK_EXPIRED)));
            return;
        }

        String errorCode = exception instanceof OAuth2AuthenticationException oauth ? oauth.getError().getErrorCode() : "unknown";
        String reason = "access_denied".equals(errorCode) ? "OAUTH_CANCELLED" : "OAUTH_FAILED";
        boolean linking = OAuth2LinkRequests.linkUserId(request).isPresent();
        log.info("소셜 {} 실패: {} ({})", linking ? "계정 연결" : "로그인", errorCode, reason);
        response.sendRedirect(frontend.url(linking ? OAuth2LinkRequests.failurePath(reason) : "/login?error=" + reason));
    }

    /** 예외의 원인 사슬에서 원하는 종류를 찾습니다. (시작 필터는 원래 예외를 한 번 감싸서 넘김) */
    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }
}
