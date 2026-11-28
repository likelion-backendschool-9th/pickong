package com.pickong.conversation.application.port.out;

import com.pickong.conversation.domain.model.Conversation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 대화방 + 참여자 저장소. */
public interface ConversationPersistencePort {

    /** 참여자(나간 참여자 포함)와 함께 */
    Optional<Conversation> findById(long conversationId);

    /**
     * 대화방 행을 SELECT … FOR UPDATE로 잠근 뒤 참여자와 함께 읽습니다. 메시지를 저장하기 전에 잠가서, 같은 대화방 안에서는
     * 메시지 id 순서와 커밋 순서가 같게 합니다(늦게 커밋된 메시지를 after 보충에서 놓치지 않음, blueprint 5.4.4).
     */
    Optional<Conversation> findByIdForUpdate(long conversationId);

    /** 마지막 메시지 시각으로 updated_at을 바꿉니다. */
    void touch(long conversationId, OffsetDateTime at);

    /** 새 그룹과 참여자를 저장합니다. @return 대화방 id */
    long saveGroup(Conversation group);

    /**
     * 처음 들어오는 그룹 참여자를 저장합니다.
     *
     * @param lastReadMessageId 들어올 때까지의 메시지는 읽은 것으로 (이전 대화는 보이지만 안 읽은 수에 세지 않음). 없으면 null
     */
    void addMembers(long conversationId, List<Long> userIds, OffsetDateTime joinedAt, Long lastReadMessageId);

    void rename(long conversationId, String title);

    void setMuted(long memberId, boolean muted);

    /**
     * 새 1:1 대화방을 저장합니다. 같은 {@code directKey}의 대화방이 이미 있으면(동시에 만든 경우 포함) 저장하지 않습니다.
     * 참여자는 대화방을 새로 만들었을 때만 저장합니다.
     */
    SavedDirect saveDirectIfAbsent(Conversation direct);

    /** 나간 참여자를 다시 참여시킵니다 (나간 시각을 지우고 참여 시각을 바꿈). 참여 중이면 아무것도 하지 않습니다. */
    void rejoin(long memberId, OffsetDateTime joinedAt);

    /** 참여자를 나가게 합니다. 이미 나갔으면 아무것도 하지 않습니다. */
    void leave(long memberId, OffsetDateTime leftAt);

    /**
     * 읽음 위치를 앞으로만 옮깁니다. 동시에 들어와도 지금 값보다 작으면 바꾸지 않습니다.
     *
     * @return 실제로 옮겼으면 true
     */
    boolean advanceLastRead(long memberId, long messageId);

    /**
     * @param created 이번에 새로 만들었으면 true
     */
    record SavedDirect(long conversationId, boolean created) {
    }
}
