package com.pickong.conversation.domain.model;

import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 대화방 참여자. Conversation 애그리거트 안에서만 다룹니다. 마지막 읽은 메시지는 뒤로 가지 않습니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConversationMember {
    private Long id;
    private Long userId;
    private Long lastReadMessageId;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;

    public boolean isActive() {
        return leftAt == null;
    }
}
