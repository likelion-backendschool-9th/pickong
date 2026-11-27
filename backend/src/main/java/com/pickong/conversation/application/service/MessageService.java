package com.pickong.conversation.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.conversation.application.dto.ReadReceipt;
import com.pickong.conversation.application.dto.SendMessageCommand;
import com.pickong.conversation.application.dto.SentMessage;
import com.pickong.conversation.application.port.in.MessageUseCase;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import com.pickong.conversation.application.port.out.MessagePersistencePort;
import com.pickong.conversation.application.port.out.MessagePersistencePort.StoredMessage;
import com.pickong.conversation.application.port.out.MessagePushPort;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.model.Message;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 보내기 · 읽음 (blueprint 5.4.4). 실시간 전달은 MessagePushPort가 커밋 후에 합니다.
 * <p>차단한 · 차단당한 상대에게 보내기 거절은 user_blocks 마이그레이션(#179) 이후에 추가합니다.
 */
@Service
@Transactional
public class MessageService implements MessageUseCase {

    private final ConversationPersistencePort conversations;
    private final MessagePersistencePort messages;
    private final ConversationQueryPort queries;
    private final MessagePushPort push;
    private final Clock clock;

    public MessageService(ConversationPersistencePort conversations, MessagePersistencePort messages,
                          ConversationQueryPort queries, MessagePushPort push, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.queries = queries;
        this.push = push;
        this.clock = clock;
    }

    /**
     * 본문 확인(domain) → 대화방 행 잠금 → 참여 확인 → 같은 clientMessageId 확인 → (1:1) 나간 상대 다시 참여
     * → 저장 · 대화방 시각 갱신 · 보낸 사람 읽음 위치 → 커밋 후 전달.
     * <p>잠금 덕분에 같은 대화방의 메시지는 id 순서대로 커밋되고, 같은 clientMessageId 두 개가 동시에 와도 하나만 저장됩니다.
     */
    @Override
    public void send(SendMessageCommand command) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Message message = Message.write(command.conversationId(), command.senderId(), command.clientMessageId(),
                command.text(), now);

        Conversation conversation = conversations.findByIdForUpdate(command.conversationId())
                .orElseThrow(() -> new BusinessException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
        ConversationMember sender = conversation.ensureActiveMember(command.senderId());

        Optional<StoredMessage> existing = messages.findBySenderAndClientMessageId(command.senderId(), command.clientMessageId());
        if (existing.isPresent()) {
            if (existing.get().conversationId() != command.conversationId()) {
                // 다른 대화방에서 이미 쓴 clientMessageId (프론트 오류)
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            push.resendToSender(loadSent(existing.get().id()));
            return;
        }

        for (ConversationMember left : conversation.membersToRejoinOnNewMessage()) {
            conversations.rejoin(left.getId(), now);
        }
        long id = messages.save(message);
        conversations.touch(conversation.getId(), now);
        conversations.advanceLastRead(sender.getId(), id);

        push.pushMessage(conversation.recipientsOfNewMessage(), loadSent(id));
    }

    /**
     * 참여 확인 → 읽을 메시지 정하기(보낸 값 또는 마지막 메시지) → 앞으로 갈 때만 저장 → 실제로 옮겼으면 커밋 후 READ_UPDATED.
     * 동시에 들어온 요청끼리 뒤로 가지 않는 것은 저장소의 조건부 UPDATE가 보장합니다.
     */
    @Override
    public void markRead(long userId, long conversationId, Long lastReadMessageId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
        ConversationMember me = conversation.ensureActiveMember(userId);

        Long target = lastReadMessageId;
        if (target == null) {
            target = messages.findLatestVisibleId(conversationId, me.getJoinedAt()).orElse(null);
            if (target == null) {
                return;
            }
        } else if (!messages.existsVisible(conversationId, target, me.getJoinedAt())) {
            throw new BusinessException(ConversationErrorCode.MESSAGE_NOT_FOUND);
        }

        long readTo = target;
        if (me.readUpTo(readTo) != me && conversations.advanceLastRead(me.getId(), readTo)) {
            queries.findUserPublicId(userId).ifPresent(publicId -> push.pushRead(conversation.activeMemberIds(),
                    new ReadReceipt(conversationId, publicId, readTo)));
        }
    }

    private SentMessage loadSent(long messageId) {
        return queries.findSentMessage(messageId)
                .orElseThrow(() -> new IllegalStateException("방금 저장한 메시지를 읽지 못했습니다: " + messageId));
    }
}
