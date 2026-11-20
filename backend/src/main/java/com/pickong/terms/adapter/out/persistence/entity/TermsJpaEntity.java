package com.pickong.terms.adapter.out.persistence.entity;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
