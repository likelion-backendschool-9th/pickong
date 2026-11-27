package com.pickong.media.application.dto;

/**
 * Presigned URL 발급 결과
 *
 * @param mediaId     업로드 확인과 게시물 작성 때 쓸 사진 id
 * @param uploadUrl   브라우저가 사진을 PUT으로 올릴 Presigned URL (5분 동안만 쓸 수 있음)
 * @param contentType PUT 요청의 Content-Type 헤더에 그대로 넣어야 하는 값 (서명에 들어가서 다르면 R2가 거부함)
 */
public record UploadTicket(Long mediaId, String uploadUrl, String contentType) {
}
