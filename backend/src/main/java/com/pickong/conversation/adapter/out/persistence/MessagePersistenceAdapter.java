package com.pickong.conversation.adapter.out.persistence;

import com.pickong.conversation.adapter.out.persistence.entity.MessageJpaEntity;
import com.pickong.conversation.adapter.out.persistence.repository.MessageJpaRepository;
import com.pickong.conversation.application.port.out.MessagePersistencePort;
import com.pickong.conversation.domain.model.Message;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MessagePersistenceAdapter implements MessagePersistencePort {

    private final MessageJpaRepository messages;
    private final JsonMapper json;

    public MessagePersistenceAdapter(MessageJpaRepository messages, JsonMapper json) {
        this.messages = messages;
        this.json = json;
    }

    /** visibleFrom이 null이면(그룹) 처음부터 */
    @Override
    public Optional<Long> findLatestVisibleId(long conversationId, OffsetDateTime visibleFrom) {
        return visibleFrom == null
                ? messages.findLatestIdFromStart(conversationId)
                : messages.findLatestVisibleId(conversationId, visibleFrom);
    }

    @Override
    public boolean existsVisible(long conversationId, long messageId, OffsetDateTime visibleFrom) {
        return visibleFrom == null
                ? messages.existsFromStart(conversationId, messageId)
                : messages.existsVisible(conversationId, messageId, visibleFrom);
    }

    /** 안내 메시지는 SystemEvent를 JSON으로 저장합니다(system_payload). */
    @Override
    public long save(Message message) {
        String payload = message.getSystemEvent() == null ? null : json.writeValueAsString(message.getSystemEvent());
        return messages.save(MessageJpaEntity.from(message, payload)).getId();
    }

    @Override
    public Optional<StoredMessage> findBySenderAndClientMessageId(long senderId, UUID clientMessageId) {
        return messages.findBySenderIdAndClientMessageId(senderId, clientMessageId)
                .map(m -> new StoredMessage(m.getId(), m.getConversationId()));
    }
}
