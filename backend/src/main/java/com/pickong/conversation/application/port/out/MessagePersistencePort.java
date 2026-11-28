package com.pickong.conversation.application.port.out;

import com.pickong.conversation.domain.model.Message;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 메시지 저장소. "볼 수 있는 메시지"는 지우지 않았고 {@code visibleFrom}(1:1의 참여 시각) 이후에 보낸 메시지입니다.
 * {@code visibleFrom}이 null이면(그룹) 처음부터입니다.
 */
public interface MessagePersistencePort {

    Optional<Long> findLatestVisibleId(long conversationId, OffsetDateTime visibleFrom);

    boolean existsVisible(long conversationId, long messageId, OffsetDateTime visibleFrom);

    /** @return 저장한 메시지의 id */
    long save(Message message);

    /** 이 회원이 이 clientMessageId로 이미 보낸 메시지 (대화방과 관계없이, UNIQUE(sender_id, client_message_id)) */
    Optional<StoredMessage> findBySenderAndClientMessageId(long senderId, UUID clientMessageId);

    record StoredMessage(long id, long conversationId) {
    }
}
