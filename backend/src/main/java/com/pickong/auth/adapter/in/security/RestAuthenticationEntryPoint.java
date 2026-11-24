package com.pickong.auth.adapter.in.security;

import com.pickong.common.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 로그인이 필요한 경로에 인증 없이 들어오면 401 UNAUTHORIZED.
 * (기본 동작인 로그인 페이지 리다이렉트 대신 JSON으로 응답해 프론트가 재발급을 시도하게 합니다)
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter writer;

    public RestAuthenticationEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    /**
     * Spring Security가 "로그인이 필요한데 로그인 정보가 없다"고 판단했을 때 호출합니다.
     * (쿠키가 없거나, 토큰이 만료 · 위조되어 인증 필터가 로그인 정보를 넣지 못한 경우)
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        writer.write(response, CommonErrorCode.UNAUTHORIZED);
    }
}
