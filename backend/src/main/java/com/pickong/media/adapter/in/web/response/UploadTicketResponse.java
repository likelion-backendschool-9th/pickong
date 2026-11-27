package com.pickong.media.adapter.in.web.response;

import com.pickong.media.application.dto.UploadTicket;

/**
 * Presigned URL 발급 응답
 * 브라우저는 uploadUrl로 PUT을 보낼 때 Content-Type 헤더에 contentType을 그대로 넣어야 함
 */
public record UploadTicketResponse(Long mediaId, String uploadUrl, String contentType) {

    public static UploadTicketResponse from(UploadTicket ticket) {
        return new UploadTicketResponse(ticket.mediaId(), ticket.uploadUrl(), ticket.contentType());
    }
}
