package com.pickong.conversation.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.conversation.application.dto.ConversationPartner;
import com.pickong.conversation.application.dto.MessageResult;
import com.pickong.conversation.application.dto.ReadReceipt;
import com.pickong.conversation.application.dto.SendMessageCommand;
import com.pickong.conversation.application.dto.SentMessage;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import com.pickong.conversation.application.port.out.MessagePersistencePort;
import com.pickong.conversation.application.port.out.MessagePersistencePort.StoredMessage;
import com.pickong.conversation.application.port.out.MessagePushPort;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.vo.ConversationType;
import com.pickong.conversation.domain.vo.MessageType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessageServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-28T03:00:00Z");
    private static final long CONVERSATION = 50;
    private static final long ME = 1;
    private static final long PARTNER = 2;
    private static final UUID CLIENT_ID = UUID.randomUUID();

    private final ConversationPersistencePort conversations = mock(ConversationPersistencePort.class);
    private final MessagePersistencePort messages = mock(MessagePersistencePort.class);
    private final ConversationQueryPort queries = mock(ConversationQueryPort.class);
    private final MessagePushPort push = mock(MessagePushPort.class);
    private final MessageService service = new MessageService(conversations, messages, queries, push,
            Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

    @Test
    @DisplayName("보내기: 잠금 → 저장 → 대화방 시각 · 내 읽음 위치 → 참여자 전원에게 전달")
    void send() {
        direct(member(10, ME, null), member(11, PARTNER, null));
        when(messages.findBySenderAndClientMessageId(ME, CLIENT_ID)).thenReturn(Optional.empty());
        when(messages.save(any())).thenReturn(900L);
        SentMessage sent = sent(900);
        when(queries.findSentMessage(900)).thenReturn(Optional.of(sent));

        service.send(new SendMessageCommand(ME, CONVERSATION, CLIENT_ID, "안녕"));

        var order = inOrder(conversations, messages, push);
        order.verify(conversations).findByIdForUpdate(CONVERSATION);
        order.verify(messages).save(any());
        order.verify(conversations).touch(CONVERSATION, NOW);
        order.verify(conversations).advanceLastRead(10, 900);
        order.verify(push).pushMessage(List.of(ME, PARTNER), sent);
        verify(conversations, never()).rejoin(anyLong(), any());
    }

    @Test
    @DisplayName("메시지 시각은 대화방을 잠근 뒤에 정한다 (id가 큰 메시지가 시각이 더 이르지 않게)")
    void timeIsTakenAfterLock() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW.toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        MessageService withClock = new MessageService(conversations, messages, queries, push, clock);
        direct(member(10, ME, null), member(11, PARTNER, null));
        when(messages.findBySenderAndClientMessageId(ME, CLIENT_ID)).thenReturn(Optional.empty());
        when(messages.save(any())).thenReturn(900L);
        when(queries.findSentMessage(900)).thenReturn(Optional.of(sent(900)));

        withClock.send(new SendMessageCommand(ME, CONVERSATION, CLIENT_ID, "안녕"));

        var order = inOrder(conversations, clock);
        order.verify(conversations).findByIdForUpdate(CONVERSATION);
        order.verify(clock).instant();
    }

    @Test
    @DisplayName("1:1에서 나간 상대는 저장 전에 다시 참여시키고 받는 사람에 넣는다")
    void rejoinsLeftPartner() {
        direct(member(10, ME, null), member(11, PARTNER, NOW.minusDays(1)));
        when(messages.findBySenderAndClientMessageId(ME, CLIENT_ID)).thenReturn(Optional.empty());
        when(messages.save(any())).thenReturn(900L);
        when(queries.findSentMessage(900)).thenReturn(Optional.of(sent(900)));

        service.send(new SendMessageCommand(ME, CONVERSATION, CLIENT_ID, "다시 볼래?"));

        var order = inOrder(conversations, messages);
        order.verify(conversations).rejoin(11, NOW);
        order.verify(messages).save(any());
        verify(push).pushMessage(eq(List.of(ME, PARTNER)), any());
    }

    @Test
    @DisplayName("같은 clientMessageId로 다시 오면 저장하지 않고 보낸 사람에게만 다시 확인")
    void duplicateIsResentToSenderOnly() {
        direct(member(10, ME, null), member(11, PARTNER, null));
        when(messages.findBySenderAndClientMessageId(ME, CLIENT_ID)).thenReturn(Optional.of(new StoredMessage(900, CONVERSATION)));
        SentMessage sent = sent(900);
        when(queries.findSentMessage(900)).thenReturn(Optional.of(sent));

        service.send(new SendMessageCommand(ME, CONVERSATION, CLIENT_ID, "안녕"));

        verify(push).resendToSender(sent);
        verify(push, never()).pushMessage(any(), any());
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("다른 대화방에서 쓴 clientMessageId는 400 INVALID_INPUT")
    void clientIdReusedInOtherConversation() {
        direct(member(10, ME, null), member(11, PARTNER, null));
        when(messages.findBySenderAndClientMessageId(ME, CLIENT_ID)).thenReturn(Optional.of(new StoredMessage(900, 77)));

        assertThatThrownBy(() -> service.send(new SendMessageCommand(ME, CONVERSATION, CLIENT_ID, "안녕")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
        verifyNoInteractions(push);
    }

    @Test
    @DisplayName("참여하지 않은 대화방에는 보낼 수 없다 (404), 저장 · 전달 없음")
    void notMember() {
        direct(member(10, 3, null), member(11, PARTNER, null));

        assertThatThrownBy(() -> service.send(new SendMessageCommand(ME, CONVERSATION, CLIENT_ID, "안녕")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ConversationErrorCode.CONVERSATION_NOT_FOUND);
        verify(messages, never()).save(any());
        verifyNoInteractions(push);
    }

    @Test
    @DisplayName("본문 오류는 대화방을 잠그기 전에 거절")
    void invalidTextBeforeLock() {
        assertThatThrownBy(() -> service.send(new SendMessageCommand(ME, CONVERSATION, CLIENT_ID, " ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ConversationErrorCode.EMPTY_MESSAGE);
        verifyNoInteractions(conversations, push);
    }

    @Test
    @DisplayName("읽음: 실제로 옮겼을 때만 참여 중인 회원에게 READ_UPDATED")
    void readPushesOnlyWhenAdvanced() {
        Conversation conversation = Conversation.builder().id(CONVERSATION).type(ConversationType.DIRECT)
                .members(List.of(member(10, ME, null), member(11, PARTNER, null))).build();
        when(conversations.findById(CONVERSATION)).thenReturn(Optional.of(conversation));
        when(messages.existsVisible(eq(CONVERSATION), anyLong(), any())).thenReturn(true);
        UUID myPublicId = UUID.randomUUID();
        when(queries.findUserPublicId(ME)).thenReturn(Optional.of(myPublicId));

        when(conversations.advanceLastRead(10, 900)).thenReturn(true);
        service.markRead(ME, CONVERSATION, 900L);
        verify(push).pushRead(List.of(ME, PARTNER), new ReadReceipt(CONVERSATION, myPublicId, 900));

        when(conversations.advanceLastRead(10, 901)).thenReturn(false); // 동시에 다른 요청이 더 뒤까지 옮김
        service.markRead(ME, CONVERSATION, 901L);
        verify(push, never()).pushRead(any(), eq(new ReadReceipt(CONVERSATION, myPublicId, 901)));
    }

    private void direct(ConversationMember... members) {
        Conversation conversation = Conversation.builder().id(CONVERSATION).type(ConversationType.DIRECT)
                .members(List.of(members)).build();
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(conversation));
    }

    private static ConversationMember member(long id, long userId, OffsetDateTime leftAt) {
        return ConversationMember.builder().id(id).userId(userId).joinedAt(NOW.minusDays(3)).leftAt(leftAt).build();
    }

    private static SentMessage sent(long id) {
        MessageResult message = new MessageResult(id, CONVERSATION, new ConversationPartner(UUID.randomUUID(), "me", null, null),
                true, MessageType.TEXT, "안녕", null, NOW);
        return new SentMessage(message, ME, CLIENT_ID);
    }

    @Test
    @DisplayName("메시지가 없을 때 '마지막까지 읽음'은 아무것도 하지 않는다")
    void readWithoutMessages() {
        when(conversations.findById(CONVERSATION)).thenReturn(Optional.of(Conversation.builder().id(CONVERSATION)
                .type(ConversationType.DIRECT).members(List.of(member(10, ME, null))).build()));
        when(messages.findLatestVisibleId(eq(CONVERSATION), any())).thenReturn(Optional.empty());

        service.markRead(ME, CONVERSATION, null);

        verify(conversations, never()).advanceLastRead(anyLong(), anyLong());
        verifyNoInteractions(push);
    }
}
