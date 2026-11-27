package com.pickong.conversation.adapter.in.websocket.payload;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.conversation.application.dto.SendMessageCommand;
import com.pickong.conversation.domain.ConversationErrorCode;
import java.util.UUID;

/**
 * SEND /app/dm.send 본문 (common 5.9): {@code {"conversationId":"42","clientMessageId":"UUID","text":"안녕","attachment":null}}.
 * ID는 문자열입니다. 첨부는 아직 받지 않습니다.
 */
public record SendMessagePayload(String conversationId, String clientMessageId, String text, Object attachment) {

    /** @throws BusinessException 400 INVALID_INPUT — id 형식이 틀림, 400 ATTACHMENT_NOT_SUPPORTED — 첨부를 보냄 */
    public SendMessageCommand toCommand(long senderId) {
        if (attachment != null) {
            throw new BusinessException(ConversationErrorCode.ATTACHMENT_NOT_SUPPORTED);
        }
        return new SendMessageCommand(senderId, PayloadIds.positiveLong(conversationId), uuid(clientMessageId), text);
    }

    private static UUID uuid(String value) {
        try {
            if (value != null) {
                return UUID.fromString(value);
            }
        } catch (IllegalArgumentException ignored) {
            // 아래에서 INVALID_INPUT
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
}
