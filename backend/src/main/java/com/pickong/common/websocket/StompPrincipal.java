package com.pickong.common.websocket;

import java.security.Principal;

/**
 * STOMP 세션의 사용자. 이름은 회원 내부 id 문자열이라 사용자 목적지(/user/queue/**)의 키가 됩니다.
 * 아이디(username)는 바꿀 수 있어 키로 쓰지 않습니다 (blueprint 5.4.3).
 */
public record StompPrincipal(String name) implements Principal {

    public static StompPrincipal of(long userId) {
        return new StompPrincipal(String.valueOf(userId));
    }

    @Override
    public String getName() {
        return name;
    }

    /** STOMP 컨트롤러에서 회원 id로 바꿀 때 씁니다. */
    public static long userIdOf(Principal principal) {
        return Long.parseLong(principal.getName());
    }
}
