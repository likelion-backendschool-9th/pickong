package com.pickong.media.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * media_files 테이블 JPA 엔티티.
 * 업로드된 미디어 파일 메타데이터를 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "media_files")
public class MediaFileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 파일 처리 상태. 값 목록: PENDING, UPLOADED, PROCESSED, FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 스토리지 키. 업로드 완료 후 세팅. */
    @Column(name = "storage_key", unique = true)
    private String storageKey;

    /** 접근 URL. 처리 완료 후 세팅. */
    @Column(name = "url")
    private String url;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
