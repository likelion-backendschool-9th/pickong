package com.pickong.conversation.application.dto;

import java.util.UUID;

/** 누군가 읽음 위치를 옮김 (READ_UPDATED). {@code readerPublicId}는 회원 공개 id */
public record ReadReceipt(long conversationId, UUID readerPublicId, long lastReadMessageId) {
}
