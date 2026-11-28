package com.pickong.conversation.adapter.out.persistence;

import com.pickong.conversation.adapter.out.persistence.entity.ConversationJpaEntity;
import com.pickong.conversation.adapter.out.persistence.entity.ConversationMemberJpaEntity;
import com.pickong.conversation.adapter.out.persistence.repository.ConversationJpaRepository;
import com.pickong.conversation.adapter.out.persistence.repository.ConversationMemberJpaRepository;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ConversationPersistenceAdapter implements ConversationPersistencePort {

    private final ConversationJpaRepository conversations;
    private final ConversationMemberJpaRepository members;

    public ConversationPersistenceAdapter(ConversationJpaRepository conversations, ConversationMemberJpaRepository members) {
        this.conversations = conversations;
        this.members = members;
    }

    @Override
    public Optional<Conversation> findById(long conversationId) {
        return conversations.findById(conversationId).map(c -> c.toDomain(
                members.findByConversationIdOrderByIdAsc(c.getId()).stream().map(ConversationMemberJpaEntity::toDomain).toList()));
    }

    @Override
    public SavedDirect saveDirectIfAbsent(Conversation direct) {
        boolean created = conversations.insertDirectIfAbsent(direct.getDirectKey(), direct.getCreatedBy(), direct.getCreatedAt()) == 1;
        ConversationJpaEntity saved = conversations.findByDirectKey(direct.getDirectKey())
                .orElseThrow(() -> new IllegalStateException("1:1 대화방을 찾지 못했습니다: " + direct.getDirectKey()));
        if (created) {
            for (ConversationMember member : direct.getMembers()) {
                members.save(ConversationMemberJpaEntity.of(saved.getId(), member));
            }
            members.flush();
        }
        return new SavedDirect(saved.getId(), created);
    }

    @Override
    public void rejoin(long memberId, OffsetDateTime joinedAt) {
        members.rejoin(memberId, joinedAt);
    }

    @Override
    public void leave(long memberId, OffsetDateTime leftAt) {
        members.leave(memberId, leftAt);
    }

    @Override
    public void advanceLastRead(long memberId, long messageId) {
        members.advanceLastRead(memberId, messageId);
    }
}
