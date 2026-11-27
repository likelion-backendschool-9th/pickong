package com.pickong.common.websocket;

import java.util.Collection;

/**
 * 회원에게 STOMP 메시지를 보내는 유일한 출구. 모듈의 Push 어댑터는 SimpMessagingTemplate 대신 이것만 씁니다
 * (직접 쓰면 그 서버에 연결된 회원에게만 가서, 서버가 여러 대일 때 메시지가 빠집니다).
 * <p>
 * 구현은 {@code pickong.redis.host}(REDIS_HOST)로 정해집니다: 있으면 {@link RedisUserPushRelay}, 없으면 {@link LocalUserPushRelay}.
 * 실패해도 예외를 던지지 않습니다. 메시지의 원본은 DB이고 클라이언트는 다시 연결할 때 HTTP로 보충합니다 (blueprint 5.4.5).
 *
 * @see WebSocketSessionAttributes
 */
public interface UserPushRelay {

    /**
     * 모든 서버의, 이 회원들의 세션으로 보냅니다.
     *
     * @param destination 사용자 목적지에서 /user를 뺀 값 (예: "/queue/dm")
     * @param payload     JSON으로 바꿀 수 있는 값
     */
    void publish(Collection<Long> userIds, String destination, Object payload);

    /**
     * 이 서버에 연결된 이 회원의 세션으로만 보냅니다. 보낸 사람의 전송 확인처럼 이 서버에 연결된 것이 확실할 때 씁니다.
     * Redis를 거치지 않으므로 Redis 장애 중에도 전달됩니다.
     */
    void sendLocal(long userId, String destination, Object payload);
}
