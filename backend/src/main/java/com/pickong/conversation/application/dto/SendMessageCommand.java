package com.pickong.conversation.application.dto;

import java.util.UUID;

/**
 * 메시지 보내기 (STOMP /app/dm.send).
 *
 * @param clientMessageId 프론트가 메시지마다 만드는 UUID. 다시 보낼 때도 같은 값
 */
public record SendMessageCommand(long senderId, long conversationId, UUID clientMessageId, String text) {
}
