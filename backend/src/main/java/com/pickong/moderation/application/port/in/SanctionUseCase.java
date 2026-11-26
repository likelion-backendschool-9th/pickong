package com.pickong.moderation.application.port.in;

import com.pickong.moderation.application.dto.SanctionCommand;
import com.pickong.moderation.domain.model.Sanction;

public interface SanctionUseCase {
    Sanction sanction(SanctionCommand command);

    Sanction revoke(long userId, long sanctionId, long adminId, String reason);

    /** 사용자 단위 만료 정리 진입점. 자동 만료에 가짜 관리자나 관리자 로그를 만들지 않습니다. */
    int expireDue(long userId);
}
