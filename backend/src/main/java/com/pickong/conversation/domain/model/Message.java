package com.pickong.conversation.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 메시지 애그리거트. soft delete.
 * <p>보내기(STOMP)와 첨부는 다음 이슈에서 추가합니다. 그때 본문 2,000자 · "본문 또는 첨부 하나" 규칙을 여기에 둡니다.
 *
 * <p>{@code clientMessageId}는 프론트가 메시지마다 만드는 UUID로, 같은 값이 다시 오면 새로 저장하지 않습니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Message {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private UUID clientMessageId;
    private String content;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
}
