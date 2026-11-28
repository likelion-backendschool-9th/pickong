package com.pickong.conversation.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.MessagePageQuery;
import com.pickong.conversation.application.dto.MessagePageResult;
import com.pickong.conversation.application.dto.MessageResult;
import com.pickong.conversation.application.port.in.ConversationQueryUseCase;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConversationQueryService implements ConversationQueryUseCase {

    private final ConversationPersistencePort conversations;
    private final ConversationQueryPort queries;

    public ConversationQueryService(ConversationPersistencePort conversations, ConversationQueryPort queries) {
        this.conversations = conversations;
        this.queries = queries;
    }

    @Override
    public List<ConversationSummaryResult> myConversations(long userId) {
        return queries.findSummaries(userId);
    }

    @Override
    public ConversationSummaryResult getDetail(long userId, long conversationId) {
        return queries.findSummary(userId, conversationId)
                .orElseThrow(() -> new BusinessException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
    }

    /**
     * 한 개 더 읽어서 다음 페이지가 있는지 봅니다. 결과는 항상 오래된 순입니다.
     * <ul>
     *   <li>cursor(또는 처음): 최신 쪽에서 size개 → 오래된 순으로 뒤집음. 더 있으면 nextCursor = 가장 오래된 id</li>
     *   <li>after: 오래된 쪽에서 size개. 더 있으면 nextCursor = 가장 새 id (다음 after 값)</li>
     * </ul>
     */
    @Override
    public MessagePageResult messages(long userId, long conversationId, MessagePageQuery query) {
        if (query.cursor() != null && query.after() != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
        ConversationMember me = conversation.ensureActiveMember(userId);
        // 1:1은 참여 시각 이후, 그룹은 처음부터 (초대된 사람도 이전 대화를 전부 봄)
        OffsetDateTime visibleFrom = conversation.visibleFrom(me);
        int size = query.size();

        if (query.after() != null) {
            List<MessageResult> rows = queries.findMessagesAfter(userId, conversationId, visibleFrom, query.after(), size + 1);
            boolean hasMore = rows.size() > size;
            List<MessageResult> items = hasMore ? rows.subList(0, size) : rows;
            return new MessagePageResult(List.copyOf(items), hasMore ? items.getLast().id() : null);
        }

        List<MessageResult> rows = queries.findMessagesBefore(userId, conversationId, visibleFrom, query.cursor(), size + 1);
        boolean hasMore = rows.size() > size;
        List<MessageResult> items = new ArrayList<>(hasMore ? rows.subList(0, size) : rows);
        Collections.reverse(items);
        return new MessagePageResult(List.copyOf(items), hasMore ? items.getFirst().id() : null);
    }

    @Override
    public long unreadCount(long userId) {
        return queries.countUnread(userId);
    }
}
