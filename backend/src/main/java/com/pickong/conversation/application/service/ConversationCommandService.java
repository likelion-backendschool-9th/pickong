package com.pickong.conversation.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.OpenDirectResult;
import com.pickong.conversation.application.port.in.ConversationCommandUseCase;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.application.port.out.ConversationPersistencePort.SavedDirect;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.user.application.dto.UserSummary;
import com.pickong.user.application.port.in.UserQueryUseCase;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1:1 대화방 열기 · 나가기.
 * <p>차단한 · 차단당한 상대와의 대화방 열기 거절은 user_blocks 마이그레이션(#179) 이후에 추가합니다.
 */
@Service
@Transactional
public class ConversationCommandService implements ConversationCommandUseCase {

    private final ConversationPersistencePort conversations;
    private final ConversationQueryPort queries;
    private final UserQueryUseCase users;
    private final Clock clock;

    public ConversationCommandService(ConversationPersistencePort conversations, ConversationQueryPort queries,
                                      UserQueryUseCase users, Clock clock) {
        this.conversations = conversations;
        this.queries = queries;
        this.users = users;
        this.clock = clock;
    }

    /**
     * 상대 확인(ACTIVE) → 나 자신 확인(domain) → 없으면 만들기(DB UNIQUE로 하나만) → 이미 있었는데 내가 나갔으면 다시 참여.
     * 상대가 나간 상태여도 상대를 다시 참여시키지 않습니다. 상대는 새 메시지를 받을 때 다시 참여합니다(보내기 이슈).
     */
    @Override
    public OpenDirectResult openDirect(long userId, String partnerUsername) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        UserSummary partner = users.getActiveByUsername(partnerUsername);
        SavedDirect saved = conversations.saveDirectIfAbsent(Conversation.openDirect(userId, partner.id(), now));

        if (!saved.created()) {
            // 메시지 보내기와 같은 대화방 행 잠금을 거친 뒤 참여 시각을 정해, 경계에 걸린 메시지가 보이지 않는 일이 없게 합니다.
            ConversationMember me = lock(saved.conversationId()).memberOf(userId)
                    .orElseThrow(() -> new IllegalStateException("1:1 대화방에 참여자 행이 없습니다: " + saved.conversationId()));
            if (!me.isActive()) {
                conversations.rejoin(me.getId(), me.rejoin(OffsetDateTime.now(clock)).getJoinedAt());
            }
        }

        ConversationSummaryResult summary = queries.findDirectSummary(userId, saved.conversationId())
                .orElseThrow(() -> new IllegalStateException("방금 연 대화방을 읽지 못했습니다: " + saved.conversationId()));
        return new OpenDirectResult(summary, saved.created());
    }

    @Override
    public void leave(long userId, long conversationId) {
        ConversationMember me = lock(conversationId).ensureActiveMember(userId);
        conversations.leave(me.getId(), me.leave(OffsetDateTime.now(clock)).getLeftAt());
    }

    /** 참여 상태를 바꿀 때는 메시지 보내기와 같은 대화방 행 잠금(SELECT … FOR UPDATE)을 거칩니다. */
    private Conversation lock(long conversationId) {
        return conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new BusinessException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
    }
}
