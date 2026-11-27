package com.pickong.common.websocket;

/**
 * 핸드셰이크 때 WebSocket 세션 속성에 넣는 값의 키. auth의 핸드셰이크 인터셉터가 넣고, 여기와 auth의 목적지 확인이 읽습니다.
 */
public final class WebSocketSessionAttributes {

    /** 회원 내부 id (Long). STOMP Principal 이름이 됩니다. */
    public static final String USER_ID = "pickong.userId";

    /** Access Token 만료 시각 (Instant). 지나면 연결을 닫습니다. */
    public static final String ACCESS_TOKEN_EXPIRES_AT = "pickong.accessTokenExpiresAt";

    private WebSocketSessionAttributes() {
    }
}
