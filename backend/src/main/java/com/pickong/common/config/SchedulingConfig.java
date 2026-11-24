package com.pickong.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 예약 작업(@Scheduled)을 켭니다. 작업은 각 모듈의 {@code adapter/in/scheduler}에만 두고, in-port만 호출합니다.
 * <p>
 * 지금은 서버가 한 대라 분산 잠금(ShedLock 등)을 쓰지 않습니다. 여러 대로 늘릴 때, 여러 번 실행되면 안 되는 작업이 생기면 그때 추가합니다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
