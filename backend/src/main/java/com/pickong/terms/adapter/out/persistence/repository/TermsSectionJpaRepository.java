package com.pickong.terms.adapter.out.persistence.repository;

import com.pickong.terms.adapter.out.persistence.entity.TermsSectionJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsSectionJpaRepository extends JpaRepository<TermsSectionJpaEntity, Long> {

    /** 약관 버전 하나의 조항을 순서대로 */
    List<TermsSectionJpaEntity> findByTermsIdOrderByPositionAsc(Long termsId);
}
