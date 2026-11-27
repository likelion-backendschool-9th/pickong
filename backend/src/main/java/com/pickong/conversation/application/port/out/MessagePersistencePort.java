package com.pickong.conversation.application.port.out;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 메시지 저장소. "볼 수 있는 메시지"는 지우지 않았고 {@code visibleFrom}(참여 시각) 이후에 보낸 메시지입니다.
 * 메시지 저장(보내기)은 STOMP 이슈에서 추가합니다.
 */
public interface MessagePersistencePort {

    Optional<Long> findLatestVisibleId(long conversationId, OffsetDateTime visibleFrom);

    boolean existsVisible(long conversationId, long messageId, OffsetDateTime visibleFrom);
}
