package com.pickong.conversation.application.port.in;

import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.MessagePageQuery;
import com.pickong.conversation.application.dto.MessagePageResult;
import java.util.List;

/**
 * 대화 목록 · 상세 · 메시지 내역 · 안 읽은 수. 조회는 읽음 위치를 바꾸지 않습니다(읽음은 {@link MessageUseCase#markRead}).
 */
public interface ConversationQueryUseCase {

    /** 참여 중인 대화방(1:1 · 그룹) 중 내가 볼 수 있는 메시지가 있는 것만, 마지막 메시지가 최근인 순서 */
    List<ConversationSummaryResult> myConversations(long userId);

    /** @throws com.pickong.common.error.BusinessException 404 CONVERSATION_NOT_FOUND — 없거나 참여 중이 아님 */
    ConversationSummaryResult getDetail(long userId, long conversationId);

    /**
     * 메시지 내역 (안내 메시지 포함). 1:1은 내가 참여한 시각 이후, 그룹은 처음부터 지우지 않은 메시지가 보입니다.
     *
     * @throws com.pickong.common.error.BusinessException 404 CONVERSATION_NOT_FOUND — 없거나 참여 중이 아님,
     *         400 INVALID_INPUT — cursor와 after를 함께 보냄
     */
    MessagePageResult messages(long userId, long conversationId, MessagePageQuery query);

    /** 참여 중인 모든 대화방에서 내가 읽지 않은, 다른 사람이 보낸 글 수 (하단바 배지, 안내 메시지는 세지 않음) */
    long unreadCount(long userId);
}
