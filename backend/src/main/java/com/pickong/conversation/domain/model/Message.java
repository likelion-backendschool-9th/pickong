package com.pickong.conversation.domain.model;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.domain.ConversationErrorCode;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 메시지 애그리거트. soft delete. 지금은 본문만 보낼 수 있습니다(첨부는 별도 이슈).
 * <p>{@code clientMessageId}는 프론트가 메시지마다 만드는 UUID로, 같은 값이 다시 오면 새로 저장하지 않습니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Message {

    /** 본문 최대 길이 (코드 포인트 기준, 한글 · 이모지 한 글자 = 1). DB의 char_length 제한과 같음 */
    public static final int TEXT_MAX = 2_000;

    private Long id;
    private Long conversationId;
    private Long senderId;
    private UUID clientMessageId;
    private String content;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;

    /**
     * 새 메시지. 본문은 받은 그대로 저장합니다(앞뒤 공백 · 줄바꿈 유지).
     *
     * @throws BusinessException 400 EMPTY_MESSAGE — 비었거나 공백뿐, 400 MESSAGE_TOO_LONG — 2,000자 초과
     */
    public static Message write(long conversationId, long senderId, UUID clientMessageId, String text, OffsetDateTime now) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ConversationErrorCode.EMPTY_MESSAGE);
        }
        if (text.codePointCount(0, text.length()) > TEXT_MAX) {
            throw new BusinessException(ConversationErrorCode.MESSAGE_TOO_LONG);
        }
        return Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .clientMessageId(clientMessageId)
                .content(text)
                .createdAt(now)
                .build();
    }
}
