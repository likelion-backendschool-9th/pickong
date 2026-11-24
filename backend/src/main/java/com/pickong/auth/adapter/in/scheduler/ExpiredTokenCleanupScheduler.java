package com.pickong.auth.adapter.in.scheduler;

import com.pickong.auth.application.port.in.TokenUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래전에 만료된 Refresh Token 행을 매일 지웁니다. 로그인 · 재발급할 때마다 행이 하나씩 쌓이기 때문입니다.
 * 기준(만료 후 보관 기간)은 {@code pickong.auth.refresh-token.cleanup-retention}, 시각은 {@code cleanup-cron}(한국 시간)입니다.
 */
@Component
public class ExpiredTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenCleanupScheduler.class);

    private final TokenUseCase tokenUseCase;

    public ExpiredTokenCleanupScheduler(TokenUseCase tokenUseCase) {
        this.tokenUseCase = tokenUseCase;
    }

    /** 기본 매일 04:00(한국 시간). 실패해도 다음 날 다시 돌기 때문에 여기서 재시도하지 않습니다. */
    @Scheduled(cron = "${pickong.auth.refresh-token.cleanup-cron}", zone = "Asia/Seoul")
    public void run() {
        int deleted = tokenUseCase.deleteExpiredTokens();
        log.info("만료된 Refresh Token 정리: {}건 삭제", deleted);
    }
}
