package com.pickong.terms.adapter.out.persistence.repository;

import com.pickong.terms.adapter.out.persistence.entity.TermsJpaEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermsJpaRepository extends JpaRepository<TermsJpaEntity, Long> {

    /** 이미 시행된 약관 버전 전체 (종류별 최신순) */
    @Query("select t from TermsJpaEntity t where t.effectiveAt <= :now order by t.type, t.effectiveAt desc")
    List<TermsJpaEntity> findEffective(@Param("now") OffsetDateTime now);
}
