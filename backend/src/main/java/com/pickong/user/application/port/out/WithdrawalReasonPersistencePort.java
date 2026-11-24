package com.pickong.user.application.port.out;

import com.pickong.user.domain.vo.WithdrawalReason;
import java.time.LocalDate;

/**
 * 탈퇴 사유 저장소 (withdrawal_reasons, V11). '추가만' 하는 통계용 기록이라 domain 모델 없이 원시값으로 받습니다(blueprint 4.2).
 * 회원 id와 시각은 받지 않습니다. 남기면 누구의 사유인지 알아낼 수 있기 때문입니다.
 */
public interface WithdrawalReasonPersistencePort {

    void save(WithdrawalReason reason, String detail, LocalDate requestedOn);
}
