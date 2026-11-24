package com.pickong.auth.application.service;

import com.pickong.auth.application.dto.RestoredAccount;
import com.pickong.auth.application.port.in.AccountWithdrawalUseCase;
import com.pickong.auth.application.port.out.RestoreTokenPort;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.common.error.BusinessException;
import com.pickong.user.application.dto.WithdrawalStatus;
import com.pickong.user.application.port.in.UserWithdrawalUseCase;
import com.pickong.user.domain.UserErrorCode;
import org.springframework.stereotype.Service;

/** 탈퇴 취소 토큰을 확인하고 user의 탈퇴 규칙을 부릅니다. 트랜잭션은 user 서비스가 엽니다. */
@Service
public class AccountWithdrawalService implements AccountWithdrawalUseCase {

    private final UserWithdrawalUseCase userWithdrawal;
    private final RestoreTokenPort restoreTokens;

    public AccountWithdrawalService(UserWithdrawalUseCase userWithdrawal, RestoreTokenPort restoreTokens) {
        this.userWithdrawal = userWithdrawal;
        this.restoreTokens = restoreTokens;
    }

    @Override
    public void requestWithdrawal(Long userId, String reason, String detail) {
        userWithdrawal.requestWithdrawal(userId, reason, detail);
    }

    /** 토큰 확인(없거나 만료면 401) → 지금도 취소할 수 있는지(30일 안) 확인(아니면 409) */
    @Override
    public WithdrawalStatus getRestoreSession(String restoreToken) {
        return userWithdrawal.findCancellableWithdrawal(verify(restoreToken))
                .orElseThrow(() -> new BusinessException(UserErrorCode.WITHDRAWAL_NOT_CANCELLABLE));
    }

    @Override
    public RestoredAccount cancelWithdrawal(String restoreToken) {
        Long userId = verify(restoreToken);
        return new RestoredAccount(userId, userWithdrawal.cancelWithdrawal(userId));
    }

    private Long verify(String restoreToken) {
        return restoreTokens.verify(restoreToken).orElseThrow(() -> new BusinessException(AuthErrorCode.RESTORE_SESSION_EXPIRED));
    }
}
