package com.pickong.conversation.adapter.out.websocket.payload;

import com.pickong.conversation.application.dto.ReadReceipt;
import com.pickong.conversation.application.dto.SentMessage;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * /user/queue/dm으로 보내는 대화 이벤트 {@code {type, data}} (common 5.9). 프론트는 모르는 type을 무시합니다.
 */
public record DmEventPayload(String type, Object data) {

    public static final String MESSAGE_CREATED = "MESSAGE_CREATED";
    public static final String READ_UPDATED = "READ_UPDATED";

    /** 받는 사람에 따라 isMine이 달라집니다. */
    public static DmEventPayload messageCreated(SentMessage m, boolean mine) {
        return new DmEventPayload(MESSAGE_CREATED, new MessageCreated(String.valueOf(m.id()), String.valueOf(m.conversationId()),
                m.senderPublicId(), mine, m.text(), null, m.createdAt(), m.clientMessageId().toString()));
    }

    public static DmEventPayload readUpdated(ReadReceipt r) {
        return new DmEventPayload(READ_UPDATED, new ReadUpdated(String.valueOf(r.conversationId()), r.readerPublicId(),
                String.valueOf(r.lastReadMessageId())));
    }

    /**
     * 메시지 목록 API의 MessageDto와 같은 형식 + clientMessageId (보낸 사람의 '보내는 중' 말풍선과 짝짓기)
     *
     * @param attachment 첨부를 만들 때까지 항상 null
     */
    public record MessageCreated(String id, String conversationId, UUID senderId, boolean isMine, String text,
                                 Object attachment, OffsetDateTime createdAt, String clientMessageId) {
    }

    /** @param userId 읽은 회원의 공개 id */
    public record ReadUpdated(String conversationId, UUID userId, String lastReadMessageId) {
    }
}
