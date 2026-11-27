package com.pickong.media.domain.model;

import com.pickong.common.error.BusinessException;
import com.pickong.media.domain.MediaErrorCode;
import com.pickong.media.domain.vo.MediaStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 사진 한 장의 기록
 * 흐름: issue(PENDING) → R2에 직접 업로드 → confirm 결과에 따라 markUploaded(UPLOADED) 또는 markFailed(FAILED)
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MediaFile {

    /** 게시물 사진 한 장의 최대 크기 (10MB) */
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** 허용하는 파일 종류와 저장할 때 붙일 확장자 */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

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

    /**
     * Presigned URL을 발급하기 전에 새 기록을 만듦
     * 1. 종류 검사 → 2. 크기 검사 → 3. 추측할 수 없는 파일 이름(feeds/랜덤UUID.확장자) 생성
     * 회원 id를 이름에 넣지 않아 주소만 보고 누구 사진인지 알 수 없음
     */
    public static MediaFile issue(Long userId, String contentType, long sizeBytes) {
        String mimeType = contentType == null ? "" : contentType.trim().toLowerCase();
        String extension = EXTENSIONS.get(mimeType);
        if (extension == null) {
            throw new BusinessException(MediaErrorCode.MEDIA_TYPE_NOT_ALLOWED);
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new BusinessException(MediaErrorCode.MEDIA_TOO_LARGE);
        }
        return MediaFile.builder()
                .userId(userId)
                .status(MediaStatus.PENDING)
                .storageKey("feeds/" + UUID.randomUUID() + "." + extension)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .build();
    }

    /** 남의 사진이면 있다는 사실을 숨기려고 없는 사진과 같은 404를 냄 */
    public void ensureOwnedBy(Long viewerId) {
        if (!userId.equals(viewerId)) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND);
        }
    }

    /** 확인을 이미 통과한 사진인지 (같은 확인 요청이 다시 와도 그대로 돌려주려고 씀) */
    public boolean isConfirmed() {
        return status == MediaStatus.UPLOADED || status == MediaStatus.PROCESSED;
    }

    public boolean isFailed() {
        return status == MediaStatus.FAILED;
    }

    /** R2에 실제로 올라간 파일이 발급할 때 말한 종류와 크기와 같은지 */
    public boolean matches(long actualSizeBytes, String actualContentType) {
        return sizeBytes != null && sizeBytes == actualSizeBytes
                && actualContentType != null && mimeType.equalsIgnoreCase(actualContentType.trim());
    }

    /** 확인 통과: 보는 주소를 채우고 UPLOADED로 바꿈 */
    public void markUploaded(String publicUrl) {
        this.status = MediaStatus.UPLOADED;
        this.url = publicUrl;
    }

    /** 확인 실패: 게시물에 연결할 수 없게 FAILED로 바꿈 */
    public void markFailed() {
        this.status = MediaStatus.FAILED;
    }
}
