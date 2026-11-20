package com.pickong.media.domain.model;

import com.pickong.media.domain.vo.MediaStatus;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 미디어 파일 애그리거트. PENDING → 업로드 확인 → 처리 완료/실패, 재시도.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MediaFile {
    private Long id;
    private Long userId;
    private MediaStatus status;
    private String storageKey;
    private String url;
    private String mimeType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private int retryCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
