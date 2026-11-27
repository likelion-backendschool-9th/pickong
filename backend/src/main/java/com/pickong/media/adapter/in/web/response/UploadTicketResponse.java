package com.pickong.media.adapter.in.web.response;

import com.pickong.media.application.dto.UploadTicket;
import java.util.Map;

/**
 * Presigned URL 발급 응답
 * 브라우저는 uploadUrl로 PUT을 보낼 때 uploadHeaders를 하나도 빼거나 바꾸지 않고 그대로 넣어야 함
 * 예: { "Content-Type": "image/jpeg", "If-None-Match": "*" }
 */
public record UploadTicketResponse(Long mediaId, String uploadUrl, Map<String, String> uploadHeaders) {

    public static UploadTicketResponse from(UploadTicket ticket) {
        return new UploadTicketResponse(ticket.mediaId(), ticket.uploadUrl(), ticket.uploadHeaders());
    }
}
