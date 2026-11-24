package com.pickong.terms.adapter.out.persistence.repository;

import com.pickong.terms.adapter.out.persistence.entity.UserTermsAgreementJpaEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTermsAgreementJpaRepository extends JpaRepository<UserTermsAgreementJpaEntity, Long> {

    /** 철회하지 않은 동의 기록 한 줄: 약관 종류 · 버전과 동의 시각 (terms와 이어 붙인 결과) */
    interface ActiveAgreementRow {
        String getType();

        String getVersion();

        OffsetDateTime getAgreedAt();
    }

    /** 이 회원의 철회하지 않은 동의 기록을 최근 동의 순으로 */
    @Query("""
            select t.type as type, t.version as version, a.agreedAt as agreedAt
            from UserTermsAgreementJpaEntity a, TermsJpaEntity t
            where a.termsId = t.id and a.userId = :userId and a.revokedAt is null
            order by a.agreedAt desc, a.id desc
            """)
    List<ActiveAgreementRow> findActive(@Param("userId") Long userId);

    /** 이 종류(모든 버전)의 철회하지 않은 동의 기록에 철회 시각을 적습니다. 바뀐 줄 수를 돌려줍니다 */
    @Modifying
    @Query("""
            update UserTermsAgreementJpaEntity a set a.revokedAt = :revokedAt
            where a.userId = :userId and a.revokedAt is null
              and a.termsId in (select t.id from TermsJpaEntity t where t.type = :type)
            """)
    int revokeActive(@Param("userId") Long userId, @Param("type") String type, @Param("revokedAt") OffsetDateTime revokedAt);
}
