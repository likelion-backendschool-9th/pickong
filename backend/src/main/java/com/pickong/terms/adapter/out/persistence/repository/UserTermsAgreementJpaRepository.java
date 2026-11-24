package com.pickong.terms.adapter.out.persistence.repository;

import com.pickong.terms.adapter.out.persistence.entity.UserTermsAgreementJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsAgreementJpaRepository extends JpaRepository<UserTermsAgreementJpaEntity, Long> {
}
