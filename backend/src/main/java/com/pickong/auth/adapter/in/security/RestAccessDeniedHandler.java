package com.pickong.auth.adapter.in.security;

import com.pickong.common.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * 403 응답. CSRF 토큰 문제는 INVALID_CSRF_TOKEN으로 구분해서,
 * 프론트가 권한 문제(FORBIDDEN)와 헷갈리지 않고 XSRF-TOKEN 쿠키를 다시 읽어 한 번 재시도할 수 있게 합니다.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter writer;

    public RestAccessDeniedHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    /**
     * Spring Security가 접근을 거절했을 때 호출합니다. 두 경우가 있습니다.
     * <ul>
     *   <li>CSRF 토큰이 없거나 틀림 → 403 INVALID_CSRF_TOKEN (로그인 여부와 관계없이)</li>
     *   <li>로그인했지만 권한이 없음 → 403 FORBIDDEN</li>
     * </ul>
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        writer.write(response, exception instanceof CsrfException
                ? CommonErrorCode.INVALID_CSRF_TOKEN
                : CommonErrorCode.FORBIDDEN);
    }
}
