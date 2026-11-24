package com.pickong.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각은 {@link Clock} 빈으로 받습니다.
 * 토큰 만료처럼 시간에 따라 달라지는 로직을 테스트에서 고정된 시각으로 검증하기 위해서입니다.
 */
@Configuration
public class ClockConfig {

    /** 운영에서 쓰는 실제 시계(UTC). 테스트는 {@code Clock.fixed(...)}로 바꿔 넣습니다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
