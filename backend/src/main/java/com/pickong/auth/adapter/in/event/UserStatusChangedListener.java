package com.pickong.auth.adapter.in.event;

import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.user.domain.event.UserStatusChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** 청사진의 커밋 후 폐기 정책. 회원의 모든 Refresh Token을 별도 트랜잭션에서 폐기합니다. */
@Component
public class UserStatusChangedListener {
    private final TokenUseCase tokens;
    private final TransactionTemplate afterCommitTransaction;

    public UserStatusChangedListener(TokenUseCase tokens, PlatformTransactionManager transactionManager) {
        this.tokens = tokens;
        this.afterCommitTransaction = new TransactionTemplate(transactionManager);
        // AFTER_COMMIT에 남아 있는 원래 트랜잭션 리소스를 재사용하면 폐기 쓰기가 커밋되지 않을 수 있습니다.
        this.afterCommitTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserStatusChangedEvent event) {
        if ("SUSPENDED".equals(event.status()) || "BANNED".equals(event.status())) {
            afterCommitTransaction.executeWithoutResult(status -> tokens.revokeAllSessions(event.userId()));
        }
    }
}
