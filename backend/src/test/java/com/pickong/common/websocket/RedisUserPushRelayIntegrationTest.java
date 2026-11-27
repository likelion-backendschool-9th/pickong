package com.pickong.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.pickong.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;

/**
 * REDIS_HOST가 있으면 Redis Pub/Sub으로 중계합니다: 발행 → (Redis) → 구독 → 이 서버의 회원 세션 (blueprint 5.4.5).
 * Redis 컨테이너는 이 테스트에만 띄웁니다. 공통 TestcontainersConfiguration에는 넣지 않습니다(다른 테스트에 Redis 불필요).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers
class RedisUserPushRelayIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("pickong.redis.host", REDIS::getHost);
        registry.add("pickong.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    UserPushRelay relay;

    @MockitoSpyBean
    SimpMessagingTemplate messaging;

    @Test
    @DisplayName("REDIS_HOST가 있으면 Redis 중계를 쓰고, 발행한 내용이 Redis를 거쳐 각 회원에게 같은 JSON으로 간다")
    void publishGoesThroughRedis() {
        assertThat(relay).isInstanceOf(RedisUserPushRelay.class);

        relay.publish(List.of(7L, 8L), "/queue/dm", Map.of("type", "MESSAGE_CREATED", "data", Map.of("text", "안녕")));

        verify(messaging, timeout(5_000)).convertAndSendToUser(eq("7"), eq("/queue/dm"), argThat(RedisUserPushRelayIntegrationTest::isHello));
        verify(messaging, timeout(5_000)).convertAndSendToUser(eq("8"), eq("/queue/dm"), argThat(RedisUserPushRelayIntegrationTest::isHello));
    }

    @Test
    @DisplayName("sendLocal은 Redis를 거치지 않고 바로 보낸다 (보낸 사람의 전송 확인)")
    void sendLocalSkipsRedis() {
        relay.sendLocal(9L, "/queue/dm", Map.of("type", "MESSAGE_CREATED"));

        verify(messaging).convertAndSendToUser(eq("9"), eq("/queue/dm"), any(Object.class));
    }

    private static boolean isHello(Object payload) {
        return payload instanceof JsonNode node
                && "MESSAGE_CREATED".equals(node.path("type").asString())
                && "안녕".equals(node.path("data").path("text").asString());
    }
}
