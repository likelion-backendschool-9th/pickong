package com.pickong.media.adapter.out.persistence.entity;

import com.pickong.media.domain.model.MediaFile;
import com.pickong.media.domain.vo.MediaStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * media_files 테이블 JPA 엔티티 (V21)
 * 업로드한 사진 한 장의 기록을 저장함
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

    /** 도메인 → 엔티티. 새 기록이면 생성 시각을 채우고, 저장할 때마다 수정 시각을 바꿈 */
    public static MediaFileJpaEntity from(MediaFile media) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MediaFileJpaEntity entity = new MediaFileJpaEntity();
        entity.id = media.getId();
        entity.userId = media.getUserId();
        entity.status = media.getStatus().name();
        entity.storageKey = media.getStorageKey();
        entity.url = media.getUrl();
        entity.mimeType = media.getMimeType();
        entity.sizeBytes = media.getSizeBytes();
        entity.width = media.getWidth();
        entity.height = media.getHeight();
        entity.retryCount = media.getRetryCount();
        entity.createdAt = media.getCreatedAt() != null ? media.getCreatedAt() : now;
        entity.updatedAt = now;
        return entity;
    }

    /** 엔티티 → 도메인 */
    public MediaFile toDomain() {
        return MediaFile.builder()
                .id(id)
                .userId(userId)
                .status(MediaStatus.valueOf(status))
                .storageKey(storageKey)
                .url(url)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .width(width)
                .height(height)
                .retryCount(retryCount)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
