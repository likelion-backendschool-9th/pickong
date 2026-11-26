package com.pickong.moderation.adapter.in.event;

import com.pickong.moderation.application.port.in.SanctionUseCase;
import com.pickong.user.domain.event.UserSuspensionExpiryCheckEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 기존 moderation → user 의존 방향으로, 인증 시점에도 동일한 만료 UseCase를 실행합니다. */
@Component
public class UserSuspensionExpiryCheckListener {
    private final SanctionUseCase sanctions;
    public UserSuspensionExpiryCheckListener(SanctionUseCase sanctions) { this.sanctions = sanctions; }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(UserSuspensionExpiryCheckEvent event) { sanctions.expireDue(event.userId()); }
}
