package com.pickong.conversation.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 저장된 메시지를 실시간으로 보낼 때 쓰는 값. 받는 사람마다 isMine이 달라서 보낸 사람의 내부 id를 함께 둡니다
 * (내부 id는 응답으로 내보내지 않음).
 */
public record SentMessage(long id, long conversationId, long senderId, UUID senderPublicId, UUID clientMessageId,
                          String text, OffsetDateTime createdAt) {
}
