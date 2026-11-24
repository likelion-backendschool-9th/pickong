package com.pickong.user.adapter.out.persistence.repository;

import com.pickong.user.adapter.out.persistence.entity.WithdrawalReasonJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalReasonJpaRepository extends JpaRepository<WithdrawalReasonJpaEntity, Long> {
}
