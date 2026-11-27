package com.pickong.common.websocket;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis Pub/Sub으로 모든 서버에 뿌리고, 각 서버는 구독해서 자기에게 연결된 회원에게 보냅니다 (blueprint 5.4.5).
 * 자기가 발행한 것도 구독해서 받으므로 서버가 1대여도 같은 경로입니다. 연결되지 않은 회원의 메시지는 버려집니다.
 * <p>
 * 채널 내용: {@code {"userIds":["12","34"],"destination":"/queue/dm","payload":{…}}}
 */
class RedisUserPushRelay implements UserPushRelay, MessageListener {

    static final String CHANNEL = "pickong:ws:user-push";

    private static final Logger log = LoggerFactory.getLogger(RedisUserPushRelay.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final LocalUserPushRelay local;

    RedisUserPushRelay(StringRedisTemplate redis, JsonMapper json, LocalUserPushRelay local) {
        this.redis = redis;
        this.json = json;
        this.local = local;
    }

    /** 발행 실패는 로그만 남깁니다. 메시지는 이미 저장됐고 받는 쪽은 다시 연결할 때 보충합니다. */
    @Override
    public void publish(Collection<Long> userIds, String destination, Object payload) {
        if (userIds.isEmpty()) {
            return;
        }
        try {
            Envelope envelope = new Envelope(userIds.stream().map(String::valueOf).toList(), destination,
                    json.valueToTree(payload));
            redis.convertAndSend(CHANNEL, json.writeValueAsString(envelope));
        } catch (RuntimeException e) {
            log.warn("Redis 발행 실패 (destination={}, 받는 사람 {}명)", destination, userIds.size(), e);
        }
    }

    @Override
    public void sendLocal(long userId, String destination, Object payload) {
        local.sendLocal(userId, destination, payload);
    }

    /** 구독: 이 서버에 연결된 세션으로 보냅니다. */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Envelope envelope = json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), Envelope.class);
            for (String userId : envelope.userIds()) {
                local.sendLocal(Long.parseLong(userId), envelope.destination(), envelope.payload());
            }
        } catch (RuntimeException e) {
            log.warn("Redis로 받은 메시지를 처리하지 못했습니다", e);
        }
    }

    record Envelope(List<String> userIds, String destination, JsonNode payload) {
    }
}
