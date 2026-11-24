package com.pickong.user.adapter.out.persistence;

import com.pickong.user.adapter.out.persistence.entity.WithdrawalReasonJpaEntity;
import com.pickong.user.adapter.out.persistence.repository.WithdrawalReasonJpaRepository;
import com.pickong.user.application.port.out.WithdrawalReasonPersistencePort;
import com.pickong.user.domain.vo.WithdrawalReason;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class WithdrawalReasonPersistenceAdapter implements WithdrawalReasonPersistencePort {

    private final WithdrawalReasonJpaRepository repository;

    public WithdrawalReasonPersistenceAdapter(WithdrawalReasonJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(WithdrawalReason reason, String detail, LocalDate requestedOn) {
        repository.save(WithdrawalReasonJpaEntity.of(reason.name(), detail, requestedOn));
    }
}
