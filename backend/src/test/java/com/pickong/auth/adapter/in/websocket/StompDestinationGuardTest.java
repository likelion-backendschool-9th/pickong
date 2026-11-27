package com.pickong.auth.adapter.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pickong.common.websocket.StompPrincipal;
import com.pickong.common.websocket.StompSessionRejector;
import com.pickong.common.websocket.WebSocketSessionAttributes;
import com.pickong.user.application.port.in.UserAccountUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class StompDestinationGuardTest {

    private static final Instant NOW = Instant.parse("2026-11-28T03:00:00Z");
    private static final long USER = 7;

    private final UserAccountUseCase accounts = mock(UserAccountUseCase.class);
    private final StompSessionRejector rejector = mock(StompSessionRejector.class);
    private final StompDestinationGuard guard = new StompDestinationGuard(accounts, rejector, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("허용: CONNECT, /user/queue/** 구독, /app/** 보내기")
    void allows() {
        when(accounts.canLogin(USER)).thenReturn(true);

        assertThat(guard.preSend(frame(StompCommand.CONNECT, null, true, NOW.plusSeconds(60)), null)).isNotNull();
        assertThat(guard.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/dm", true, NOW.plusSeconds(60)), null)).isNotNull();
        assertThat(guard.preSend(frame(StompCommand.SEND, "/app/dm.send", true, NOW.plusSeconds(60)), null)).isNotNull();
        verifyNoInteractions(rejector);
    }

    @Test
    @DisplayName("사용자 없이 CONNECT하면 UNAUTHORIZED")
    void connectWithoutUser() {
        assertRejected(frame(StompCommand.CONNECT, null, false, NOW.plusSeconds(60)), StompSessionRejector.UNAUTHORIZED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/all", "/queue/dm", "/user/7/queue/dm", "/user/queue/../topic", "/app/dm.send"})
    @DisplayName("/user/queue/** 밖 구독은 FORBIDDEN_DESTINATION")
    void subscribeOutsideUserQueue(String destination) {
        assertRejected(frame(StompCommand.SUBSCRIBE, destination, true, NOW.plusSeconds(60)),
                StompSessionRejector.FORBIDDEN_DESTINATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/8/queue/dm", "/queue/dm", "/topic/x"})
    @DisplayName("/app/** 밖으로 보내기(다른 회원 큐로 직접 보내기 등)는 FORBIDDEN_DESTINATION")
    void sendOutsideApp(String destination) {
        assertRejected(frame(StompCommand.SEND, destination, true, NOW.plusSeconds(60)),
                StompSessionRejector.FORBIDDEN_DESTINATION);
    }

    @Test
    @DisplayName("Access Token 만료 시각이 지났으면 TOKEN_EXPIRED (같은 시각 포함)")
    void expired() {
        assertRejected(frame(StompCommand.SEND, "/app/dm.send", true, NOW), StompSessionRejector.TOKEN_EXPIRED);
        assertRejected(frame(StompCommand.SUBSCRIBE, "/user/queue/dm", true, NOW.minusSeconds(1)),
                StompSessionRejector.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("보낼 때 계정이 ACTIVE가 아니면 ACCOUNT_UNAVAILABLE")
    void inactiveAccount() {
        when(accounts.canLogin(USER)).thenReturn(false);

        assertRejected(frame(StompCommand.SEND, "/app/dm.send", true, NOW.plusSeconds(60)),
                StompSessionRejector.ACCOUNT_UNAVAILABLE);
    }

    /** 예외 대신 ERROR를 보내고(rejector) 프레임을 버립니다(null). */
    private void assertRejected(Message<?> message, String code) {
        assertThat(guard.preSend(message, null)).isNull();
        verify(rejector).reject(any(), eq(code));
        clearInvocations(rejector);
    }

    private static Message<byte[]> frame(StompCommand command, String destination, boolean withUser, Instant expiresAt) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (withUser) {
            accessor.setUser(StompPrincipal.of(USER));
        }
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketSessionAttributes.USER_ID, USER);
        attributes.put(WebSocketSessionAttributes.ACCESS_TOKEN_EXPIRES_AT, expiresAt);
        accessor.setSessionAttributes(attributes);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
