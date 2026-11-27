package com.pickong.conversation.adapter.in.websocket.payload;

/**
 * SEND /app/dm.read 본문: {@code {"conversationId":"42","lastReadMessageId":"1001"}}.
 * {@code lastReadMessageId}가 없으면 볼 수 있는 마지막 메시지까지 읽음으로 처리합니다(HTTP PATCH /read와 같음).
 */
public record ReadPayload(String conversationId, String lastReadMessageId) {

    public long conversationIdValue() {
        return PayloadIds.positiveLong(conversationId);
    }

    public Long messageId() {
        return lastReadMessageId == null ? null : PayloadIds.positiveLong(lastReadMessageId);
    }
}
