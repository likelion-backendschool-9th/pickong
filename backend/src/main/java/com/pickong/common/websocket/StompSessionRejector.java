package com.pickong.common.websocket;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 연결 단위로 거절합니다: {@code message} 헤더가 코드인 STOMP ERROR 프레임을 보내고 연결을 닫습니다 (common 5.9).
 * <p>
 * ChannelInterceptor에서 예외를 던지면 안 되는 이유: 수신 순서 보장(setPreserveReceiveOrder)을 켜면 Spring이 그 예외를
 * 삼켜 ERROR 프레임이 클라이언트에 가지 않습니다 (spring-framework#33937). 그래서 인터셉터는 이 클래스로 ERROR를
 * clientOutboundChannel에 직접 보내고 프레임은 버립니다(null 반환). Spring은 ERROR를 보낸 뒤 연결을 닫습니다.
 */
@Component
public class StompSessionRejector {

    /** Access Token 만료. 프론트는 재발급 후 다시 연결합니다. */
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    /** /app/** 밖으로 보내기, /user/queue/** 밖 구독 */
    public static final String FORBIDDEN_DESTINATION = "FORBIDDEN_DESTINATION";
    /** 로그인 정보 없이 CONNECT */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    /** 연결 중에 계정이 정지 · 탈퇴 등으로 ACTIVE가 아니게 됨 */
    public static final String ACCOUNT_UNAVAILABLE = "ACCOUNT_UNAVAILABLE";

    /** WebSocket 설정이 만드는 채널이라, 설정이 이 빈(을 쓰는 인터셉터)을 받는 순환을 피하려고 나중에 꺼냅니다. */
    private final ObjectProvider<MessageChannel> clientOutboundChannel;

    public StompSessionRejector(@Qualifier("clientOutboundChannel") ObjectProvider<MessageChannel> clientOutboundChannel) {
        this.clientOutboundChannel = clientOutboundChannel;
    }

    /** @param clientFrame 거절할 클라이언트 프레임 (세션 id를 씀) */
    public void reject(StompHeaderAccessor clientFrame, String code) {
        StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
        error.setMessage(code);
        error.setSessionId(clientFrame.getSessionId());
        error.setLeaveMutable(true);
        clientOutboundChannel.getObject().send(MessageBuilder.createMessage(new byte[0], error.getMessageHeaders()));
    }
}
