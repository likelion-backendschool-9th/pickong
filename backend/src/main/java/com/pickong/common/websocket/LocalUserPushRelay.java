package com.pickong.common.websocket;

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Redis 없이 이 서버의 세션으로만 보냅니다. REDIS_HOST를 정하지 않은 로컬 실행 · 테스트 · 서버 1대 환경용입니다.
 * 서버가 여러 대이면 다른 서버에 연결된 회원에게는 가지 않으므로 운영에서는 Redis를 켭니다.
 */
class LocalUserPushRelay implements UserPushRelay {

    private static final Logger log = LoggerFactory.getLogger(LocalUserPushRelay.class);

    private final SimpMessagingTemplate messaging;

    LocalUserPushRelay(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publish(Collection<Long> userIds, String destination, Object payload) {
        userIds.forEach(id -> sendLocal(id, destination, payload));
    }

    @Override
    public void sendLocal(long userId, String destination, Object payload) {
        try {
            messaging.convertAndSendToUser(String.valueOf(userId), destination, payload);
        } catch (RuntimeException e) {
            log.warn("STOMP 전달 실패 (userId={}, destination={})", userId, destination, e);
        }
    }
}
