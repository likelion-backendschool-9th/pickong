package com.pickong.user.application.port.in;

import com.pickong.user.application.dto.WithdrawalStatus;
import java.util.List;
import java.util.Optional;

/**
 * 회원 탈퇴: 신청 · 취소(30일 안) · 익명화(30일 뒤). 정책은 backend_blueprint.md '회원 탈퇴 결정'.
 * 신청은 auth의 탈퇴 API가, 취소는 auth의 탈퇴 취소 API가, 익명화는 이 모듈의 예약 작업이 부릅니다.
 */
public interface UserWithdrawalUseCase {

    /**
     * 탈퇴를 신청합니다: WITHDRAWN_PENDING으로 바꾸고, 탈퇴 사유는 회원과 연결하지 않고 따로 저장합니다.
     * 세션 폐기는 부른 쪽(auth)이 이 트랜잭션이 커밋된 뒤에 합니다.
     *
     * @param reason 탈퇴 사유 (RARELY_USED · MISSING_FEATURE · PRIVACY · OTHER_SERVICE · ETC, 대소문자 무관)
     * @param detail 기타 내용. ETC면 필수, 300자까지
     * @throws com.pickong.common.error.BusinessException 400 INVALID_WITHDRAWAL_REASON, 409 WITHDRAWAL_NOT_ALLOWED, 404 USER_NOT_FOUND
     */
    void requestWithdrawal(Long userId, String reason, String detail);

    /** 탈퇴를 취소할 수 있는 회원이면 탈퇴 상태를, 아니면(탈퇴 중이 아님 · 30일 지남 · 회원 없음) 빈 값 */
    Optional<WithdrawalStatus> findCancellableWithdrawal(Long userId);

    /**
     * 탈퇴를 취소하고 ACTIVE로 돌립니다. 세션 발급은 부른 쪽(auth)이 합니다.
     *
     * @return 취소한 회원의 아이디
     * @throws com.pickong.common.error.BusinessException 409 WITHDRAWAL_NOT_CANCELLABLE, 404 USER_NOT_FOUND
     */
    String cancelWithdrawal(Long userId);

    /** 익명화할 때가 된(신청 후 30일이 지난) 회원 id. 예약 작업이 한 명씩 {@link #anonymize}를 부릅니다 */
    List<Long> findWithdrawalsDueForAnonymization();

    /**
     * 한 회원을 익명화합니다(회원마다 트랜잭션). 같은 트랜잭션에서 UserWithdrawnEvent를 동기로 발행해 다른 모듈이 데이터를 지우게 하고,
     * 한 곳이라도 실패하면 모두 되돌아가 회원은 WITHDRAWN_PENDING으로 남습니다(다음 실행에서 다시 처리).
     * 이미 익명화됐거나 취소된 회원이면 아무것도 하지 않습니다.
     *
     * @return 익명화했으면 true
     */
    boolean anonymize(Long userId);
}
