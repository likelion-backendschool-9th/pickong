package com.pickong.common.websocket;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * Access Token 만료 시각이 지난 연결을 닫습니다 (blueprint 5.4.3). 프레임을 보내는 연결은 목적지 확인이 먼저 거절하지만,
 * 받기만 하는 연결은 프레임이 오지 않으므로 여기서 주기적으로 닫습니다. 로그아웃 · 탈퇴 뒤에도 연결이 남는 시간이 토큰 수명 이하가 됩니다.
 * <p>
 * 프론트는 연결이 닫히면 HTTP로 상태를 확인하고(401이면 재발급) 다시 연결합니다 (common 5.9).
 */
@Component
public class ExpiredSessionCloser {

    static final CloseStatus TOKEN_EXPIRED = CloseStatus.POLICY_VIOLATION.withReason(StompSessionRejector.TOKEN_EXPIRED);

    private static final Logger log = LoggerFactory.getLogger(ExpiredSessionCloser.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public ExpiredSessionCloser(Clock clock) {
        this.clock = clock;
    }

    /** 연결이 열리고 닫힐 때 목록에 넣고 뺍니다. */
    WebSocketHandler track(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void closeExpiredSessions() {
        closeExpired(clock.instant());
    }

    /** @return 닫은 연결 수 */
    int closeExpired(Instant now) {
        int closed = 0;
        for (WebSocketSession session : sessions.values()) {
            if (session.getAttributes().get(WebSocketSessionAttributes.ACCESS_TOKEN_EXPIRES_AT) instanceof Instant expiresAt
                    && !now.isBefore(expiresAt)) {
                try {
                    session.close(TOKEN_EXPIRED);
                    closed++;
                } catch (IOException | RuntimeException e) {
                    log.debug("만료된 WebSocket 연결을 닫지 못했습니다 (session={})", session.getId(), e);
                }
                sessions.remove(session.getId());
            }
        }
        return closed;
    }
}
