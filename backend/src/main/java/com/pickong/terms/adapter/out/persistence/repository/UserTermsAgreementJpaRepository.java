package com.pickong.terms.adapter.out.persistence.repository;

import com.pickong.terms.adapter.out.persistence.entity.UserTermsAgreementJpaEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTermsAgreementJpaRepository extends JpaRepository<UserTermsAgreementJpaEntity, Long> {

    /** 철회하지 않은 동의 기록 한 줄: 동의한 약관 버전의 종류 · 버전 · 제목과 동의 시각 (terms와 이어 붙인 결과) */
    interface ActiveAgreementRow {
        String getType();

        String getVersion();

        String getTitle();

        OffsetDateTime getAgreedAt();
    }

    /** 이 회원의 철회하지 않은 동의 기록을 최근 동의 순으로 */
    @Query("""
            select t.type as type, t.version as version, t.title as title, a.agreedAt as agreedAt
            from UserTermsAgreementJpaEntity a, TermsJpaEntity t
            where a.termsId = t.id and a.userId = :userId and a.revokedAt is null
            order by a.agreedAt desc, a.id desc
            """)
    List<ActiveAgreementRow> findActive(@Param("userId") Long userId);

    /**
     * 이 약관 버전에 철회하지 않은 동의가 없을 때만 새 동의 기록을 넣습니다. 넣었으면 1, 이미 있으면 0.
     * 부분 유니크 인덱스(uk_user_terms_agreements_active, V8)에 걸리면 예외 대신 아무것도 하지 않으므로,
     * 같은 요청이 동시에 와도 기록은 하나이고 트랜잭션도 중단되지 않습니다. (ON CONFLICT는 JPQL에 없어 native)
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_terms_agreements (user_id, terms_id, agreed_at)
            VALUES (:userId, :termsId, :agreedAt)
            ON CONFLICT (user_id, terms_id) WHERE revoked_at IS NULL DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("termsId") Long termsId, @Param("agreedAt") OffsetDateTime agreedAt);

    /** 이 종류(모든 버전)의 철회하지 않은 동의 기록에 철회 시각을 적습니다. 바뀐 줄 수를 돌려줍니다 */
    @Modifying
    @Query("""
            update UserTermsAgreementJpaEntity a set a.revokedAt = :revokedAt
            where a.userId = :userId and a.revokedAt is null
              and a.termsId in (select t.id from TermsJpaEntity t where t.type = :type)
            """)
    int revokeActive(@Param("userId") Long userId, @Param("type") String type, @Param("revokedAt") OffsetDateTime revokedAt);
}
