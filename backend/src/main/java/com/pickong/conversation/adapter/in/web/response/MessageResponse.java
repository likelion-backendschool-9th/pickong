package com.pickong.conversation.adapter.in.web.response;

import com.pickong.conversation.application.dto.MessageResult;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 프론트 MessageDto. ID는 문자열(common 5.4), 보낸 사람은 공개 id(users.public_id)입니다.
 *
 * @param attachment 첨부. 보내기(STOMP) 이슈에서 형식을 정할 때까지 항상 null
 */
public record MessageResponse(
        String id,
        String conversationId,
        UUID senderId,
        boolean isMine,
        String text,
        Object attachment,
        OffsetDateTime createdAt) {

    public static MessageResponse from(MessageResult m) {
        return new MessageResponse(String.valueOf(m.id()), String.valueOf(m.conversationId()), m.senderPublicId(),
                m.mine(), m.text(), null, m.createdAt());
    }
}
