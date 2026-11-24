package com.pickong.auth.application.port.in;

import com.pickong.auth.application.dto.RestoredAccount;
import com.pickong.user.application.dto.WithdrawalStatus;

/**
 * 회원 탈퇴의 auth 쪽: 탈퇴 신청(로그인한 회원)과, 유예 중 로그인한 회원의 탈퇴 취소(탈퇴 취소 토큰).
 * 회원 상태는 user가 바꾸고, 세션 폐기 · 발급과 쿠키는 웹 어댑터가 합니다(서비스끼리 서로 부르지 않기 위해).
 */
public interface AccountWithdrawalUseCase {

    /**
     * 탈퇴를 신청합니다. 부른 쪽은 이어서 모든 세션을 폐기하고 쿠키를 지웁니다.
     *
     * @throws com.pickong.common.error.BusinessException 400 INVALID_WITHDRAWAL_REASON, 409 WITHDRAWAL_NOT_ALLOWED
     */
    void requestWithdrawal(Long userId, String reason, String detail);

    /**
     * 탈퇴 취소 화면에 보여 줄 탈퇴 상태.
     *
     * @throws com.pickong.common.error.BusinessException 401 RESTORE_SESSION_EXPIRED(쿠키 없음 · 만료), 409 WITHDRAWAL_NOT_CANCELLABLE
     */
    WithdrawalStatus getRestoreSession(String restoreToken);

    /**
     * 탈퇴를 취소합니다. 부른 쪽은 돌려받은 회원으로 세션을 발급하고 탈퇴 취소 쿠키를 지웁니다.
     *
     * @throws com.pickong.common.error.BusinessException 401 RESTORE_SESSION_EXPIRED, 409 WITHDRAWAL_NOT_CANCELLABLE
     */
    RestoredAccount cancelWithdrawal(String restoreToken);
}
