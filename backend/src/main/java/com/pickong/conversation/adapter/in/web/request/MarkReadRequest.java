package com.pickong.conversation.adapter.in.web.request;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;

/**
 * PATCH /conversations/{id}/read 요청 본문: {@code {"lastReadMessageId":"1001"}}. 본문을 생략하거나 값이 null이면
 * 지금 볼 수 있는 마지막 메시지까지 읽음으로 처리합니다. JSON의 ID는 문자열입니다(common 5.4).
 */
public record MarkReadRequest(String lastReadMessageId) {

    /** @throws BusinessException 400 INVALID_INPUT — 양의 정수 문자열이 아님 */
    public Long messageId() {
        if (lastReadMessageId == null) {
            return null;
        }
        try {
            long id = Long.parseLong(lastReadMessageId);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 INVALID_INPUT
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
}
