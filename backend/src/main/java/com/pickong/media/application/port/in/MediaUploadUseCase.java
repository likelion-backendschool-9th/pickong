package com.pickong.media.application.port.in;

import com.pickong.media.application.dto.UploadTicket;
import com.pickong.media.application.dto.UploadedMedia;

/** 사진 업로드: Presigned URL 발급과 업로드 확인 */
public interface MediaUploadUseCase {

    /**
     * Presigned URL을 발급하고 PENDING 기록을 남김
     *
     * @throws com.pickong.common.error.BusinessException 400 MEDIA_TYPE_NOT_ALLOWED (jpg, png, webp 외), 400 MEDIA_TOO_LARGE (10MB 초과)
     */
    UploadTicket issueUploadUrl(Long userId, String contentType, long sizeBytes);

    /**
     * R2에 사진이 실제로 올라갔는지 확인하고 UPLOADED로 바꿈. 이미 확인된 사진이면 그대로 돌려줌
     *
     * @throws com.pickong.common.error.BusinessException 404 MEDIA_NOT_FOUND (없거나 남의 사진),
     *         400 MEDIA_NOT_UPLOADED (R2에 파일 없음, 다시 올리면 됨), 400 MEDIA_FILE_MISMATCH (종류나 크기가 다름, FAILED로 바뀜),
     *         409 MEDIA_UPLOAD_FAILED (이미 실패한 사진)
     */
    UploadedMedia confirmUpload(Long userId, Long mediaId);
}
