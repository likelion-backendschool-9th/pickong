package com.pickong.admin.adapter.out.persistence;

import com.pickong.admin.adapter.out.persistence.entity.AdminActionLogJpaEntity;
import com.pickong.admin.adapter.out.persistence.repository.AdminActionLogJpaRepository;
import com.pickong.admin.application.dto.AdminActionLogEntry;
import com.pickong.admin.application.port.out.AdminActionLogPersistencePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminActionLogPersistenceAdapter implements AdminActionLogPersistencePort {
    private final AdminActionLogJpaRepository repository;

    public AdminActionLogPersistenceAdapter(AdminActionLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long append(AdminActionLogEntry entry) {
        return repository.saveAndFlush(AdminActionLogJpaEntity.forInsert(entry)).getId();
    }
}
