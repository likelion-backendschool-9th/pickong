package com.pickong.terms.adapter.out.persistence.entity;

import com.pickong.terms.domain.vo.TermsSection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * terms_sections 테이블 JPA 엔티티 (V8).
 * 약관 버전 한 개(terms_id)의 조항을 순서(position)대로 저장한다. 조항은 마이그레이션으로만 넣으므로 읽기만 한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "terms_sections")
public class TermsSectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "terms_id", nullable = false)
    private Long termsId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "heading", nullable = false)
    private String heading;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /** DB 행을 조항 값 객체로 바꿉니다. */
    public TermsSection toDomain() {
        return new TermsSection(heading, body);
    }
}
