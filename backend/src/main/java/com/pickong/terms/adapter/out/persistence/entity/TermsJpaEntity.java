package com.pickong.terms.adapter.out.persistence.entity;

import com.pickong.terms.domain.model.Terms;
import com.pickong.terms.domain.vo.TermsType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * terms 테이블 JPA 엔티티.
 * 약관 버전과 내용을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "terms")
public class TermsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 약관 종류. 값 목록: SERVICE, PRIVACY, MARKETING 등 */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content_url", nullable = false)
    private String contentUrl;

    @Column(name = "is_required", nullable = false)
    private boolean isRequired;

    @Column(name = "effective_at", nullable = false)
    private OffsetDateTime effectiveAt;

    /** 이 버전이 시행되면 기존 회원에게 다시 동의를 받는지 (V10) */
    @Column(name = "requires_reconsent", nullable = false)
    private boolean requiresReconsent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** DB 행을 domain 모델로 바꿉니다. 종류 문자열은 enum으로 바꿉니다. */
    public Terms toDomain() {
        return Terms.builder()
                .id(id)
                .type(TermsType.valueOf(type))
                .version(version)
                .title(title)
                .contentUrl(contentUrl)
                .required(isRequired)
                .effectiveAt(effectiveAt)
                .requiresReconsent(requiresReconsent)
                .createdAt(createdAt)
                .build();
    }
}
