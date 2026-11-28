package com.pickong.conversation.application.port.out;

import com.pickong.conversation.domain.model.Conversation;
import java.time.OffsetDateTime;
import java.util.Optional;

/** 대화방 + 참여자 저장소. */
public interface ConversationPersistencePort {

    /** 참여자(나간 참여자 포함)와 함께 */
    Optional<Conversation> findById(long conversationId);

    /**
     * 새 1:1 대화방을 저장합니다. 같은 {@code directKey}의 대화방이 이미 있으면(동시에 만든 경우 포함) 저장하지 않습니다.
     * 참여자는 대화방을 새로 만들었을 때만 저장합니다.
     */
    SavedDirect saveDirectIfAbsent(Conversation direct);

    /** 나간 참여자를 다시 참여시킵니다 (나간 시각을 지우고 참여 시각을 바꿈). 참여 중이면 아무것도 하지 않습니다. */
    void rejoin(long memberId, OffsetDateTime joinedAt);

    /** 참여자를 나가게 합니다. 이미 나갔으면 아무것도 하지 않습니다. */
    void leave(long memberId, OffsetDateTime leftAt);

    /** 읽음 위치를 앞으로만 옮깁니다. 동시에 들어와도 지금 값보다 작으면 바꾸지 않습니다. */
    void advanceLastRead(long memberId, long messageId);

    /**
     * @param created 이번에 새로 만들었으면 true
     */
    record SavedDirect(long conversationId, boolean created) {
    }
}
