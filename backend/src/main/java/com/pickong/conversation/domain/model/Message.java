package com.pickong.conversation.domain.model;

import com.pickong.conversation.domain.vo.MessageAttachmentType;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 메시지 애그리거트. 본문 또는 첨부 하나, soft delete.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Message {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String content;
    private MessageAttachmentType attachmentType;
    private Long attachmentMediaId;
    private Long attachmentPlanId;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
}
