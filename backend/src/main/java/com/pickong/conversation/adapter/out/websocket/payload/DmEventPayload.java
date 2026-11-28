package com.pickong.conversation.adapter.out.websocket.payload;

import com.pickong.conversation.application.dto.MessageResult;
import com.pickong.conversation.application.dto.ReadReceipt;
import com.pickong.conversation.domain.vo.MessageType;
import com.pickong.conversation.domain.vo.SystemEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * /user/queue/dm으로 보내는 대화 이벤트 {@code {type, data}} (common 5.9). 프론트는 모르는 type을 무시합니다.
 */
public record DmEventPayload(String type, Object data) {

    public static final String MESSAGE_CREATED = "MESSAGE_CREATED";
    public static final String READ_UPDATED = "READ_UPDATED";
    public static final String CONVERSATION_UPDATED = "CONVERSATION_UPDATED";

    /** 받는 사람에 따라 isMine이 달라집니다. */
    public static DmEventPayload messageCreated(MessageResult m, UUID clientMessageId, boolean mine) {
        Sender sender = new Sender(m.sender().publicId(), m.sender().username(),
                m.sender().name() != null ? m.sender().name() : m.sender().username(), m.sender().profileImageUrl());
        return new DmEventPayload(MESSAGE_CREATED, new MessageCreated(String.valueOf(m.id()), String.valueOf(m.conversationId()),
                m.sender().publicId(), sender, mine, m.type(), m.text(), m.system(), null, m.createdAt(),
                clientMessageId.toString()));
    }

    public static DmEventPayload readUpdated(ReadReceipt r) {
        return new DmEventPayload(READ_UPDATED, new ReadUpdated(String.valueOf(r.conversationId()), r.readerPublicId(),
                String.valueOf(r.lastReadMessageId())));
    }

    /** 대화방 정보(이름 · 참여자)가 바뀜. 프론트는 목록 · 상세를 다시 불러옵니다. */
    public static DmEventPayload conversationUpdated(long conversationId) {
        return new DmEventPayload(CONVERSATION_UPDATED, new ConversationUpdated(String.valueOf(conversationId)));
    }

    /**
     * 메시지 목록 API의 MessageDto와 같은 형식 + clientMessageId (보낸 사람의 '보내는 중' 말풍선과 짝짓기)
     *
     * @param attachment 첨부를 만들 때까지 항상 null
     */
    public record MessageCreated(String id, String conversationId, UUID senderId, Sender sender, boolean isMine,
                                 MessageType type, String text, SystemEvent system, Object attachment,
                                 OffsetDateTime createdAt, String clientMessageId) {
    }

    public record Sender(UUID id, String username, String nickname, String profileImageUrl) {
    }

    /** @param userId 읽은 회원의 공개 id */
    public record ReadUpdated(String conversationId, UUID userId, String lastReadMessageId) {
    }

    public record ConversationUpdated(String conversationId) {
    }
}
