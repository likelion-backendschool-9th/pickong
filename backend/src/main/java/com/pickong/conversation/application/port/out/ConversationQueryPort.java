package com.pickong.conversation.application.port.out;

import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.MessageResult;
import com.pickong.conversation.application.dto.SentMessage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 대화 화면 조회. 상대 · 보낸 사람 정보를 위해 users를 읽기만 합니다(JOIN, blueprint 6.1).
 * 메시지는 지우지 않았고 참여 시각 이후에 보낸 것만 셉니다.
 */
public interface ConversationQueryPort {

    /** 참여 중인 1:1 대화방 중 볼 수 있는 메시지가 있는 것, 마지막 메시지 id 내림차순 */
    List<ConversationSummaryResult> findDirectSummaries(long userId);

    /** 참여 중인 1:1 대화방 하나. 메시지가 없어도 돌려줍니다. */
    Optional<ConversationSummaryResult> findDirectSummary(long userId, long conversationId);

    long countUnread(long userId);

    /** {@code beforeId}보다 오래된 메시지(null이면 최신부터)를 id 내림차순으로 최대 {@code limit}개 */
    List<MessageResult> findMessagesBefore(long viewerId, long conversationId, OffsetDateTime visibleFrom,
                                           Long beforeId, int limit);

    /** {@code afterId}보다 새 메시지를 id 오름차순으로 최대 {@code limit}개 */
    List<MessageResult> findMessagesAfter(long viewerId, long conversationId, OffsetDateTime visibleFrom,
                                          long afterId, int limit);

    /** 실시간으로 보낼 메시지 (보낸 사람 공개 id 포함) */
    Optional<SentMessage> findSentMessage(long messageId);

    /** 회원 공개 id (users.public_id) */
    Optional<UUID> findUserPublicId(long userId);
}
