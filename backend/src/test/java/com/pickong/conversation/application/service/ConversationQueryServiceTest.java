package com.pickong.conversation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.conversation.application.dto.ConversationPartner;
import com.pickong.conversation.application.dto.MessagePageQuery;
import com.pickong.conversation.application.dto.MessagePageResult;
import com.pickong.conversation.application.dto.MessageResult;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.vo.ConversationType;
import com.pickong.conversation.domain.vo.MessageType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 메시지 페이지 계산: 한 개 더 읽어 다음 페이지를 판단하고, 결과는 항상 오래된 순 */
class ConversationQueryServiceTest {

    private static final OffsetDateTime JOINED = OffsetDateTime.parse("2026-11-28T03:00:00Z");
    private static final long ME = 1;
    private static final long CONVERSATION = 50;

    private final ConversationPersistencePort conversations = mock(ConversationPersistencePort.class);
    private final ConversationQueryPort queries = mock(ConversationQueryPort.class);
    private final ConversationQueryService service = new ConversationQueryService(conversations, queries);

    @BeforeEach
    void member() {
        when(conversations.findById(CONVERSATION)).thenReturn(Optional.of(Conversation.builder()
                .id(CONVERSATION)
                .type(ConversationType.DIRECT)
                .members(List.of(ConversationMember.join(ME, JOINED), ConversationMember.join(2, JOINED)))
                .build()));
    }

    @Test
    @DisplayName("처음 · 이전 내역: 최신 쪽 size개를 오래된 순으로, 더 있으면 nextCursor = 가장 오래된 id")
    void latestPage() {
        // 저장소는 id 내림차순으로 size + 1개를 돌려줌: 10, 9, 8, 7
        when(queries.findMessagesBefore(eq(ME), eq(CONVERSATION), eq(JOINED), isNull(), eq(4))).thenReturn(messages(10, 7));

        MessagePageResult page = service.messages(ME, CONVERSATION, new MessagePageQuery(null, null, 3));

        assertThat(page.items()).extracting(MessageResult::id).containsExactly(8L, 9L, 10L);
        assertThat(page.nextCursor()).isEqualTo(8L);
    }

    @Test
    @DisplayName("이전 내역 마지막 페이지는 nextCursor = null")
    void lastOlderPage() {
        when(queries.findMessagesBefore(ME, CONVERSATION, JOINED, 8L, 4)).thenReturn(messages(7, 6));

        MessagePageResult page = service.messages(ME, CONVERSATION, new MessagePageQuery(8L, null, 3));

        assertThat(page.items()).extracting(MessageResult::id).containsExactly(6L, 7L);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("after: 오래된 쪽 size개, 더 있으면 nextCursor = 가장 새 id (다음 after 값)")
    void afterPage() {
        // 저장소는 id 오름차순: 11, 12, 13, 14
        when(queries.findMessagesAfter(ME, CONVERSATION, JOINED, 10L, 4)).thenReturn(messages(11, 14));

        MessagePageResult page = service.messages(ME, CONVERSATION, new MessagePageQuery(null, 10L, 3));

        assertThat(page.items()).extracting(MessageResult::id).containsExactly(11L, 12L, 13L);
        assertThat(page.nextCursor()).isEqualTo(13L);
    }

    @Test
    @DisplayName("cursor와 after를 함께 보내면 400 INVALID_INPUT, 저장소를 읽지 않음")
    void cursorAndAfterTogether() {
        assertThatThrownBy(() -> service.messages(ME, CONVERSATION, new MessagePageQuery(5L, 3L, 30)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("참여하지 않은 대화방의 내역은 404")
    void notMember() {
        assertThatThrownBy(() -> service.messages(99, CONVERSATION, new MessagePageQuery(null, null, 30)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ConversationErrorCode.CONVERSATION_NOT_FOUND);
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("빈 대화방")
    void empty() {
        when(queries.findMessagesBefore(anyLong(), anyLong(), any(), any(), anyInt())).thenReturn(List.of());

        MessagePageResult page = service.messages(ME, CONVERSATION, new MessagePageQuery(null, null, 30));

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    /** from에서 to까지 (내림차순이면 from > to) */
    private static List<MessageResult> messages(long from, long to) {
        LongStream ids = from > to ? LongStream.rangeClosed(to, from).map(i -> from + to - i) : LongStream.rangeClosed(from, to);
        ConversationPartner sender = new ConversationPartner(UUID.randomUUID(), "jimin", null, null);
        return ids.mapToObj(id -> new MessageResult(id, CONVERSATION, sender, false, MessageType.TEXT, "m" + id, null,
                JOINED.plusMinutes(id))).toList();
    }
}
