package com.pickong.terms.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * user_terms_agreements 테이블 JPA 엔티티.
 * 사용자가 특정 약관에 동의한 이력을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_terms_agreements")
public class UserTermsAgreementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "terms_id", nullable = false)
    private Long termsId;

    @Column(name = "agreed_at", nullable = false)
    private OffsetDateTime agreedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    /** 새 동의 기록 한 줄을 만듭니다. (철회 시각은 비어 있음) */
    public static UserTermsAgreementJpaEntity agreed(Long userId, Long termsId, OffsetDateTime agreedAt) {
        UserTermsAgreementJpaEntity entity = new UserTermsAgreementJpaEntity();
        entity.userId = userId;
        entity.termsId = termsId;
        entity.agreedAt = agreedAt;
        return entity;
    }
}
