package com.pickong.user.adapter.in.scheduler;

import com.pickong.user.application.port.in.UserWithdrawalUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴를 신청한 지 30일이 지난 회원을 매일 익명화합니다. 시각은 {@code pickong.user.withdrawal.anonymize-cron}(한국 시간).
 * <p>
 * 회원마다 따로 처리합니다(회원마다 트랜잭션). 한 명이 실패해도(다른 모듈의 정리 실패 등) 나머지는 계속 처리하고,
 * 실패한 회원은 탈퇴 중으로 남아 다음 날 다시 처리됩니다. 처리할 회원이 없으면 아무것도 하지 않아 여러 번 돌아도 됩니다.
 */
@Component
public class WithdrawalAnonymizationScheduler {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalAnonymizationScheduler.class);

    private final UserWithdrawalUseCase withdrawals;

    public WithdrawalAnonymizationScheduler(UserWithdrawalUseCase withdrawals) {
        this.withdrawals = withdrawals;
    }

    /** 기본 매일 04:30(한국 시간). 만료 토큰 정리(04:00)와 겹치지 않게 30분 뒤입니다. */
    @Scheduled(cron = "${pickong.user.withdrawal.anonymize-cron}", zone = "Asia/Seoul")
    public void run() {
        int anonymized = 0;
        int failed = 0;
        for (Long userId : withdrawals.findWithdrawalsDueForAnonymization()) {
            try {
                if (withdrawals.anonymize(userId)) {
                    anonymized++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("탈퇴 회원 익명화 실패 (회원 {}): 다음 실행에서 다시 처리합니다", userId, e);
            }
        }
        log.info("탈퇴 회원 익명화: {}명 완료, {}명 실패", anonymized, failed);
    }
}
