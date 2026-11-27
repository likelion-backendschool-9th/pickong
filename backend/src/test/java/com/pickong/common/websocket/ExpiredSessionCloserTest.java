package com.pickong.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

class ExpiredSessionCloserTest {

    private static final Instant NOW = Instant.parse("2026-11-28T03:00:00Z");

    private final ExpiredSessionCloser closer = new ExpiredSessionCloser(Clock.fixed(NOW, ZoneOffset.UTC));
    private final WebSocketHandler tracked = closer.track(mock(WebSocketHandler.class));

    @Test
    @DisplayName("토큰 만료 시각이 지난 연결만 TOKEN_EXPIRED로 닫고, 닫힌 연결은 목록에서 빠진다")
    void closesOnlyExpired() throws Exception {
        WebSocketSession expired = session("a", NOW.minusSeconds(1));
        WebSocketSession alive = session("b", NOW.plusSeconds(60));
        tracked.afterConnectionEstablished(expired);
        tracked.afterConnectionEstablished(alive);

        assertThat(closer.closeExpired(NOW)).isEqualTo(1);
        verify(expired).close(ExpiredSessionCloser.TOKEN_EXPIRED);
        verify(alive, never()).close(ExpiredSessionCloser.TOKEN_EXPIRED);
        assertThat(closer.closeExpired(NOW)).isZero();

        tracked.afterConnectionClosed(alive, CloseStatus.NORMAL);
        assertThat(closer.closeExpired(NOW.plusSeconds(120))).isZero();
    }

    private static WebSocketSession session(String id, Instant expiresAt) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketSessionAttributes.ACCESS_TOKEN_EXPIRES_AT, expiresAt);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
