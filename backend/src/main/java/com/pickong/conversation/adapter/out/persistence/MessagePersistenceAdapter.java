package com.pickong.conversation.adapter.out.persistence;

import com.pickong.conversation.adapter.out.persistence.entity.MessageJpaEntity;
import com.pickong.conversation.adapter.out.persistence.repository.MessageJpaRepository;
import com.pickong.conversation.application.port.out.MessagePersistencePort;
import com.pickong.conversation.domain.model.Message;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MessagePersistenceAdapter implements MessagePersistencePort {

    private final MessageJpaRepository messages;

    public MessagePersistenceAdapter(MessageJpaRepository messages) {
        this.messages = messages;
    }

    @Override
    public Optional<Long> findLatestVisibleId(long conversationId, OffsetDateTime visibleFrom) {
        return messages.findLatestVisibleId(conversationId, visibleFrom);
    }

    @Override
    public boolean existsVisible(long conversationId, long messageId, OffsetDateTime visibleFrom) {
        return messages.existsVisible(conversationId, messageId, visibleFrom);
    }

    @Override
    public long save(Message message) {
        return messages.save(MessageJpaEntity.from(message)).getId();
    }

    @Override
    public Optional<StoredMessage> findBySenderAndClientMessageId(long senderId, UUID clientMessageId) {
        return messages.findBySenderIdAndClientMessageId(senderId, clientMessageId)
                .map(m -> new StoredMessage(m.getId(), m.getConversationId()));
    }
}
