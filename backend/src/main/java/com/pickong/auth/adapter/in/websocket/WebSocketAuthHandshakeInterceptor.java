package com.pickong.auth.adapter.in.websocket;

import com.pickong.auth.adapter.in.security.AuthCookies;
import com.pickong.common.security.AccessTokenVerifier;
import com.pickong.common.security.LoginUser;
import com.pickong.common.websocket.WebSocketSessionAttributes;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * /api/v1/ws 핸드셰이크. 이 요청은 이미 REST와 같은 보안 필터를 지났으므로(비로그인 · 만료 · ACTIVE가 아닌 계정 401,
 * 약관 재동의 403), 여기서는 로그인한 회원 id와 Access Token 만료 시각을 세션 속성에 넣기만 합니다.
 * JS는 httpOnly 쿠키를 읽을 수 없으므로 CONNECT 헤더가 아니라 핸드셰이크 쿠키로 인증합니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final AccessTokenVerifier verifier;

    public WebSocketAuthHandshakeInterceptor(AccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<Instant> expiresAt = accessToken(request).flatMap(verifier::expiresAt);
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user) || expiresAt.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(WebSocketSessionAttributes.USER_ID, user.userId());
        attributes.put(WebSocketSessionAttributes.ACCESS_TOKEN_EXPIRES_AT, expiresAt.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private static Optional<String> accessToken(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servlet) || servlet.getServletRequest().getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(servlet.getServletRequest().getCookies())
                .filter(c -> AuthCookies.ACCESS_TOKEN.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank())
                .findFirst();
    }
}
