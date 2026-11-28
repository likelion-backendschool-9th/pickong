package com.pickong.conversation.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.application.port.in.MessageUseCase;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.application.port.out.MessagePersistencePort;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.model.ConversationMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 읽음 처리. 보내기(send)와 실시간 전달(MessagePushPort)은 STOMP 이슈에서 추가합니다.
 */
@Service
@Transactional
public class MessageService implements MessageUseCase {

    private final ConversationPersistencePort conversations;
    private final MessagePersistencePort messages;

    public MessageService(ConversationPersistencePort conversations, MessagePersistencePort messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    /**
     * 참여 확인 → 읽을 메시지 정하기(보낸 값 또는 마지막 메시지) → 앞으로 갈 때만 저장.
     * 동시에 들어온 요청끼리 뒤로 가지 않는 것은 저장소의 조건부 UPDATE가 보장합니다.
     */
    @Override
    public void markRead(long userId, long conversationId, Long lastReadMessageId) {
        ConversationMember me = conversations.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ConversationErrorCode.CONVERSATION_NOT_FOUND))
                .ensureActiveMember(userId);

        Long target = lastReadMessageId;
        if (target == null) {
            target = messages.findLatestVisibleId(conversationId, me.getJoinedAt()).orElse(null);
            if (target == null) {
                return;
            }
        } else if (!messages.existsVisible(conversationId, target, me.getJoinedAt())) {
            throw new BusinessException(ConversationErrorCode.MESSAGE_NOT_FOUND);
        }

        if (me.readUpTo(target) != me) {
            conversations.advanceLastRead(me.getId(), target);
        }
    }
}
