package com.pickong.common.websocket;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * 핸드셰이크 인터셉터가 세션 속성에 넣은 회원 id로 STOMP 사용자를 정합니다.
 * 기본 동작(요청의 Principal 이름)을 쓰면 Spring Security 인증 객체의 이름이 회원 id가 아니라서 사용자 목적지가 맞지 않습니다.
 */
class UserIdHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Object userId = attributes.get(WebSocketSessionAttributes.USER_ID);
        return userId instanceof Long id ? StompPrincipal.of(id) : null;
    }
}
