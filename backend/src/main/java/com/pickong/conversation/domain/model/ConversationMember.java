package com.pickong.conversation.domain.model;

import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 대화방 참여자. Conversation 애그리거트 안에서만 다룹니다.
 * <ul>
 *   <li>참여 시각({@code joinedAt}) 이후의 메시지만 볼 수 있습니다. 나갔다가 다시 열면 그 시각부터 다시 보입니다</li>
 *   <li>마지막 읽은 메시지는 뒤로 가지 않습니다</li>
 * </ul>
 * 값을 직접 바꾸지 않고 바뀐 새 값을 돌려줍니다.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConversationMember {
    private Long id;
    private Long userId;
    private Long lastReadMessageId;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;
    /** 알림 끄기. 알림 기능이 생기기 전까지는 값만 저장합니다 */
    private boolean muted;

    /** 새 참여자 (id는 저장할 때 DB가 채움) */
    public static ConversationMember join(long userId, OffsetDateTime now) {
        return ConversationMember.builder().userId(userId).joinedAt(now).build();
    }

    public boolean isActive() {
        return leftAt == null;
    }

    /** 나감. 이미 나갔으면 그대로 */
    public ConversationMember leave(OffsetDateTime now) {
        return isActive() ? toBuilder().leftAt(now).build() : this;
    }

    /** 나갔던 대화방을 다시 엶. 그 시각 이후 메시지만 보이도록 참여 시각을 새로 정합니다. 참여 중이면 그대로 */
    public ConversationMember rejoin(OffsetDateTime now) {
        return isActive() ? this : toBuilder().leftAt(null).joinedAt(now).build();
    }

    /** 이 메시지까지 읽음. 이미 더 뒤까지 읽었으면 그대로 */
    public ConversationMember readUpTo(long messageId) {
        if (lastReadMessageId != null && lastReadMessageId >= messageId) {
            return this;
        }
        return toBuilder().lastReadMessageId(messageId).build();
    }
}
