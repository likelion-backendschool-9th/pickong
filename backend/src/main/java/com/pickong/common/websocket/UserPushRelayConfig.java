package com.pickong.common.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link UserPushRelay} 구현 선택 (blueprint 5.4.5). Redis는 선택입니다.
 * <ul>
 *   <li>{@code pickong.redis.host}(REDIS_HOST)가 비어 있으면 {@link LocalUserPushRelay}: Redis 연결을 만들지 않으므로
 *       로컬 실행 · 기존 테스트에 Redis가 필요 없고 /actuator/health도 Redis를 보지 않습니다</li>
 *   <li>값이 있으면 {@link RedisUserPushRelay}: 연결 · 구독을 여기서 만듭니다 (Spring Boot의 Redis 자동 설정은 application.yml에서 끔)</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class UserPushRelayConfig {

    private static final String REDIS_HOST_BLANK = "'${pickong.redis.host:}'.trim().isEmpty()";

    @Bean
    @ConditionalOnExpression(REDIS_HOST_BLANK)
    UserPushRelay localUserPushRelay(SimpMessagingTemplate messaging) {
        return new LocalUserPushRelay(messaging);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression("!" + REDIS_HOST_BLANK)
    static class Redis {

        @Bean
        LettuceConnectionFactory redisConnectionFactory(@Value("${pickong.redis.host}") String host,
                                                        @Value("${pickong.redis.port:6379}") int port,
                                                        @Value("${pickong.redis.password:}") String password) {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host.trim(), port);
            if (!password.isBlank()) {
                config.setPassword(RedisPassword.of(password));
            }
            return new LettuceConnectionFactory(config);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }

        @Bean
        RedisUserPushRelay redisUserPushRelay(StringRedisTemplate redis, JsonMapper json, SimpMessagingTemplate messaging) {
            return new RedisUserPushRelay(redis, json, new LocalUserPushRelay(messaging));
        }

        @Bean
        RedisMessageListenerContainer userPushListenerContainer(LettuceConnectionFactory connectionFactory,
                                                                RedisUserPushRelay relay) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener(relay, new ChannelTopic(RedisUserPushRelay.CHANNEL));
            return container;
        }
    }
}
