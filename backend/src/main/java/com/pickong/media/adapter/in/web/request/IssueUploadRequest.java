package com.pickong.media.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Presigned URL 발급 요청. 허용 종류와 10MB 제한은 도메인(MediaFile.issue)이 검사함
 *
 * @param contentType 올릴 파일 종류 (image/jpeg, image/png, image/webp)
 * @param sizeBytes   올릴 파일 크기 (바이트)
 */
public record IssueUploadRequest(
        @NotBlank(message = "파일 종류는 필수입니다") String contentType,
        @NotNull(message = "파일 크기는 필수입니다") @Positive(message = "파일 크기는 1바이트 이상이어야 합니다") Long sizeBytes) {
}
