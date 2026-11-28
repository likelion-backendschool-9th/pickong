package com.pickong.conversation.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 메시지 한 건.
 *
 * @param senderPublicId 보낸 사람의 공개 id (users.public_id)
 * @param mine           조회한 회원이 보낸 메시지인지
 */
public record MessageResult(long id, long conversationId, UUID senderPublicId, boolean mine, String text,
                            OffsetDateTime createdAt) {
}
