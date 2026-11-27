package com.pickong.media.application.dto;

import java.util.Map;

/**
 * Presigned URL 발급 결과
 *
 * @param mediaId       업로드 확인과 게시물 작성 때 쓸 사진 id
 * @param uploadUrl     브라우저가 사진을 PUT으로 올릴 Presigned URL (5분 동안, key에 객체가 없을 때 한 번만 쓸 수 있음)
 * @param uploadHeaders PUT 요청에 그대로 넣어야 하는 헤더 (Content-Type, If-None-Match). 서명에 들어가서 다르거나 빠지면 R2가 거부함
 */
public record UploadTicket(Long mediaId, String uploadUrl, Map<String, String> uploadHeaders) {
}
