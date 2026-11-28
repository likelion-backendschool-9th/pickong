package com.pickong.conversation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.application.dto.ConversationPartner;
import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.MessageResult;
import com.pickong.conversation.application.dto.SentMessage;
import com.pickong.conversation.application.dto.UpdateConversationCommand;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.application.port.out.ConversationPersistencePort.SavedDirect;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import com.pickong.conversation.application.port.out.MessagePersistencePort;
import com.pickong.conversation.application.port.out.MessagePushPort;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.model.Message;
import com.pickong.conversation.domain.vo.ConversationType;
import com.pickong.conversation.domain.vo.MessageType;
import com.pickong.conversation.domain.vo.SystemEvent;
import com.pickong.user.application.dto.UserSummary;
import com.pickong.user.application.port.in.UserQueryUseCase;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 1:1 다시 참여 · 그룹 만들기 · 초대 · 이름 변경 · 알림 끄기 · 나가기.
 * 참여 상태를 바꾸는 일은 메시지 보내기와 같은 대화방 행 잠금을 거치고, 그룹 변화는 안내 메시지로 남습니다.
 */
class ConversationCommandServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-28T03:00:00Z");
    private static final long CONVERSATION = 50;
    private static final long ME = 1;
    private static final long PARTNER = 2;

    private final ConversationPersistencePort conversations = mock(ConversationPersistencePort.class);
    private final MessagePersistencePort messages = mock(MessagePersistencePort.class);
    private final ConversationQueryPort queries = mock(ConversationQueryPort.class);
    private final MessagePushPort push = mock(MessagePushPort.class);
    private final UserQueryUseCase users = mock(UserQueryUseCase.class);
    private final ConversationCommandService service = new ConversationCommandService(conversations, messages, queries,
            push, users, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        for (long id = 2; id <= 20; id++) {
            when(users.getActiveByUsername("user" + id)).thenReturn(new UserSummary(id, UUID.randomUUID(), "user" + id, null, null));
        }
        when(users.getActiveByUsername("me")).thenReturn(new UserSummary(ME, UUID.randomUUID(), "me", null, null));
        when(queries.findSummary(anyLong(), anyLong())).thenReturn(Optional.of(summary()));
        when(queries.findParticipants(any())).thenAnswer(inv -> inv.<List<Long>>getArgument(0).stream()
                .map(id -> new SystemEvent.Participant(UUID.randomUUID(), "user" + id)).toList());
        when(messages.save(any())).thenReturn(900L);
        when(queries.findSentMessage(900L)).thenReturn(Optional.of(sent()));
    }

    // ---- 1:1 ----

    @Test
    @DisplayName("나갔던 1:1 대화방을 다시 열면 대화방 행을 잠근 뒤 다시 참여한다")
    void rejoinLocksConversation() {
        when(users.getActiveByUsername("jimin")).thenReturn(new UserSummary(PARTNER, UUID.randomUUID(), "jimin", null, null));
        when(conversations.saveDirectIfAbsent(any())).thenReturn(new SavedDirect(CONVERSATION, false));
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(direct(NOW.minusDays(1))));

        service.openDirect(ME, "jimin");

        verify(conversations).findByIdForUpdate(CONVERSATION);
        verify(conversations, never()).findById(anyLong());
        verify(conversations).rejoin(10, NOW);
    }

    @Test
    @DisplayName("1:1 나가기: 잠근 뒤 처리하고 안내 메시지 없이 내 다른 탭에만 알린다")
    void leaveDirect() {
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(direct(null)));

        service.leave(ME, CONVERSATION);

        verify(conversations).findByIdForUpdate(CONVERSATION);
        verify(conversations).leave(10, NOW);
        verify(messages, never()).save(any());
        verify(push).pushConversationUpdated(List.of(ME), CONVERSATION);
    }

    // ---- 그룹 만들기 ----

    @Test
    @DisplayName("그룹 만들기: 저장 → '만들기' 안내 메시지를 참여자 전원에게 (나 · 중복 아이디는 뺌)")
    void createGroup() {
        when(conversations.saveGroup(any())).thenReturn(CONVERSATION);

        service.createGroup(ME, "  성수 투어 ", List.of("user2", "user3", "user2", "me"));

        ArgumentCaptor<Conversation> group = ArgumentCaptor.forClass(Conversation.class);
        verify(conversations).saveGroup(group.capture());
        assertThat(group.getValue().getTitle()).isEqualTo("성수 투어");
        assertThat(group.getValue().getMembers()).extracting(ConversationMember::getUserId).containsExactly(ME, 2L, 3L);

        Message system = savedMessage();
        assertThat(system.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(system.getSystemEvent().event()).isEqualTo(SystemEvent.Kind.CREATED);
        assertThat(system.getSystemEvent().targets()).extracting(SystemEvent.Participant::username).containsExactly("user2", "user3");
        assertThat(system.getSystemEvent().title()).isEqualTo("성수 투어");
        verify(push).pushSystemMessage(eq(new java.util.LinkedHashSet<>(List.of(ME, 2L, 3L))), any(SentMessage.class));
    }

    @Test
    @DisplayName("그룹 인원: 나를 빼고 2명 미만이면 GROUP_TOO_SMALL, 나 포함 10명 넘으면 GROUP_FULL, 이름이 비면 INVALID_GROUP_TITLE")
    void createGroupRules() {
        assertError(() -> service.createGroup(ME, "둘이서", List.of("user2", "me")), ConversationErrorCode.GROUP_TOO_SMALL);
        List<String> ten = new ArrayList<>();
        for (int id = 2; id <= 11; id++) {
            ten.add("user" + id);
        }
        assertError(() -> service.createGroup(ME, "많이", ten), ConversationErrorCode.GROUP_FULL);
        assertError(() -> service.createGroup(ME, "  ", List.of("user2", "user3")), ConversationErrorCode.INVALID_GROUP_TITLE);
        verify(conversations, never()).saveGroup(any());
    }

    @Test
    @DisplayName("정확히 10명(나 포함)은 만들 수 있다")
    void createGroupWithTen() {
        when(conversations.saveGroup(any())).thenReturn(CONVERSATION);
        List<String> nine = new ArrayList<>();
        for (int id = 2; id <= 10; id++) {
            nine.add("user" + id);
        }

        service.createGroup(ME, "열 명", nine);

        verify(conversations).saveGroup(any());
    }

    // ---- 초대 ----

    @Test
    @DisplayName("초대: 처음 오는 사람은 지금까지 읽은 것으로 추가, 나갔던 사람은 다시 참여, 이미 있는 사람은 건너뛰고 '초대' 안내")
    void invite() {
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(group(
                member(10, ME, null), member(11, 2, null), member(12, 3, null), member(13, 4, NOW.minusDays(1)))));
        when(messages.findLatestVisibleId(CONVERSATION, null)).thenReturn(Optional.of(500L));

        service.invite(ME, CONVERSATION, List.of("user2", "user4", "user5"));

        verify(conversations).rejoin(13, NOW);
        verify(conversations).advanceLastRead(13, 500);
        verify(conversations).addMembers(CONVERSATION, List.of(5L), NOW, 500L);
        Message system = savedMessage();
        assertThat(system.getSystemEvent().event()).isEqualTo(SystemEvent.Kind.INVITED);
        assertThat(system.getSystemEvent().targets()).extracting(SystemEvent.Participant::username).containsExactly("user5", "user4");
        verify(push).pushSystemMessage(eq(new java.util.LinkedHashSet<>(List.of(ME, 2L, 3L, 5L, 4L))), any());
    }

    @Test
    @DisplayName("이미 모두 참여 중이면 아무것도 하지 않는다 (안내 메시지 없음)")
    void inviteNoop() {
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(group(
                member(10, ME, null), member(11, 2, null), member(12, 3, null))));

        service.invite(ME, CONVERSATION, List.of("user2", "user3"));

        verify(conversations, never()).addMembers(anyLong(), any(), any(), any());
        verify(messages, never()).save(any());
        verifyNoInteractions(push);
    }

    @Test
    @DisplayName("초대 후 10명을 넘으면 GROUP_FULL, 1:1에는 NOT_A_GROUP")
    void inviteRules() {
        List<ConversationMember> nine = new ArrayList<>();
        nine.add(member(10, ME, null));
        for (long id = 2; id <= 9; id++) {
            nine.add(member(10 + id, id, null));
        }
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(group(nine.toArray(ConversationMember[]::new))));
        assertError(() -> service.invite(ME, CONVERSATION, List.of("user10", "user11")), ConversationErrorCode.GROUP_FULL);

        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(direct(null)));
        assertError(() -> service.invite(ME, CONVERSATION, List.of("user3")), ConversationErrorCode.NOT_A_GROUP);
    }

    // ---- 이름 · 알림 끄기 ----

    @Test
    @DisplayName("이름 변경: 멤버 누구나, 실제로 바뀔 때만 '이름 변경' 안내. 같은 이름이면 안내 없음")
    void rename() {
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(group(
                member(10, ME, null), member(11, 2, null), member(12, 3, null))));

        service.update(2, CONVERSATION, new UpdateConversationCommand("새 이름", null));
        verify(conversations).rename(CONVERSATION, "새 이름");
        assertThat(savedMessage().getSystemEvent().event()).isEqualTo(SystemEvent.Kind.RENAMED);

        service.update(ME, CONVERSATION, new UpdateConversationCommand(" 그룹 ", null));
        verify(conversations, never()).rename(CONVERSATION, "그룹");
    }

    @Test
    @DisplayName("1:1 이름 변경은 NOT_A_GROUP, 알림 끄기는 1:1에서도 된다")
    void renameDirectAndMute() {
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(direct(null)));

        assertError(() -> service.update(ME, CONVERSATION, new UpdateConversationCommand("이름", null)), ConversationErrorCode.NOT_A_GROUP);
        service.update(ME, CONVERSATION, new UpdateConversationCommand(null, true));

        verify(conversations).setMuted(10, true);
    }

    // ---- 그룹 나가기 ----

    @Test
    @DisplayName("그룹 나가기: 남은 사람에게 '나가기' 안내, 나간 사람의 다른 탭에는 대화방이 바뀌었다고 알림")
    void leaveGroup() {
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(group(
                member(10, ME, null), member(11, 2, null), member(12, 3, null))));

        service.leave(ME, CONVERSATION);

        verify(conversations).leave(10, NOW);
        assertThat(savedMessage().getSystemEvent().event()).isEqualTo(SystemEvent.Kind.LEFT);
        verify(push).pushSystemMessage(eq(new java.util.LinkedHashSet<>(List.of(2L, 3L))), any());
        verify(push).pushConversationUpdated(List.of(ME), CONVERSATION);
    }

    // ---- 도움 ----

    private Message savedMessage() {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messages, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static void assertError(Runnable call, ConversationErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    private static Conversation direct(OffsetDateTime myLeftAt) {
        return Conversation.builder().id(CONVERSATION).type(ConversationType.DIRECT).members(List.of(
                member(10, ME, myLeftAt), member(11, PARTNER, null))).build();
    }

    private static Conversation group(ConversationMember... members) {
        return Conversation.builder().id(CONVERSATION).type(ConversationType.GROUP).title("그룹").members(List.of(members)).build();
    }

    private static ConversationMember member(long id, long userId, OffsetDateTime leftAt) {
        return ConversationMember.builder().id(id).userId(userId).joinedAt(NOW.minusDays(3)).leftAt(leftAt).build();
    }

    private static ConversationSummaryResult summary() {
        return new ConversationSummaryResult(CONVERSATION, ConversationType.DIRECT, null,
                new ConversationPartner(UUID.randomUUID(), "jimin", null, null), List.of(), 2, null, 0, false, NOW, NOW);
    }

    private static SentMessage sent() {
        MessageResult m = new MessageResult(900, CONVERSATION, new ConversationPartner(UUID.randomUUID(), "me", null, null),
                true, MessageType.SYSTEM, null, SystemEvent.left(new SystemEvent.Participant(UUID.randomUUID(), "me")), NOW);
        return new SentMessage(m, ME, UUID.randomUUID());
    }
}
