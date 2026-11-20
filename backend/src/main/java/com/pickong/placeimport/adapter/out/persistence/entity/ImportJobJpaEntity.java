package com.pickong.placeimport.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * import_jobs 테이블 JPA 엔티티.
 * AI 장소 추출 작업을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "import_jobs")
public class ImportJobJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "url", nullable = false)
    private String url;

    /** 작업 상태. 값 목록: PENDING, RUNNING, DONE, FAILED, CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 최종 추출 결과 요약. JSONB → String 저장. */
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
