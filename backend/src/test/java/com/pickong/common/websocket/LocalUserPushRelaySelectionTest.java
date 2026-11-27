package com.pickong.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickong.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.web.servlet.MockMvc;

/** REDIS_HOST가 없으면 Redis 없이 시작합니다: 서버 안 전달, Redis 연결 없음, 상태 확인은 UP (blueprint 5.4.5). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LocalUserPushRelaySelectionTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("Redis 없이: LocalUserPushRelay, RedisConnectionFactory 빈 없음, /actuator/health UP")
    void startsWithoutRedis() throws Exception {
        assertThat(context.getBean(UserPushRelay.class)).isInstanceOf(LocalUserPushRelay.class);
        assertThat(context.getBeanNamesForType(RedisConnectionFactory.class)).isEmpty();

        mvc.perform(get("/api/v1/actuator/health").servletPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
