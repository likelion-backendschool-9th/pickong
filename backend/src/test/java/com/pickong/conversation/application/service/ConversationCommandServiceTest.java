package com.pickong.conversation.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pickong.conversation.application.dto.ConversationPartner;
import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.port.out.ConversationPersistencePort;
import com.pickong.conversation.application.port.out.ConversationPersistencePort.SavedDirect;
import com.pickong.conversation.application.port.out.ConversationQueryPort;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.vo.ConversationType;
import com.pickong.user.application.dto.UserSummary;
import com.pickong.user.application.port.in.UserQueryUseCase;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 참여 상태(다시 참여 · 나가기)는 메시지 보내기와 같은 대화방 행 잠금을 거친다 */
class ConversationCommandServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-28T03:00:00Z");
    private static final long CONVERSATION = 50;
    private static final long ME = 1;
    private static final long PARTNER = 2;

    private final ConversationPersistencePort conversations = mock(ConversationPersistencePort.class);
    private final ConversationQueryPort queries = mock(ConversationQueryPort.class);
    private final UserQueryUseCase users = mock(UserQueryUseCase.class);
    private final ConversationCommandService service = new ConversationCommandService(conversations, queries, users,
            Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(users.getActiveByUsername("jimin")).thenReturn(new UserSummary(PARTNER, UUID.randomUUID(), "jimin", null, null));
        when(queries.findDirectSummary(ME, CONVERSATION)).thenReturn(Optional.of(new ConversationSummaryResult(CONVERSATION,
                new ConversationPartner(UUID.randomUUID(), "jimin", null, null), null, 0, NOW)));
    }

    @Test
    @DisplayName("나갔던 1:1 대화방을 다시 열면 대화방 행을 잠근 뒤 다시 참여한다")
    void rejoinLocksConversation() {
        when(conversations.saveDirectIfAbsent(any())).thenReturn(new SavedDirect(CONVERSATION, false));
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(direct(NOW.minusDays(1))));

        service.openDirect(ME, "jimin");

        verify(conversations).findByIdForUpdate(CONVERSATION);
        verify(conversations, never()).findById(anyLong());
        verify(conversations).rejoin(10, NOW);
    }

    @Test
    @DisplayName("나가기도 대화방 행을 잠근 뒤 처리한다")
    void leaveLocksConversation() {
        when(conversations.findByIdForUpdate(CONVERSATION)).thenReturn(Optional.of(direct(null)));

        service.leave(ME, CONVERSATION);

        verify(conversations).findByIdForUpdate(CONVERSATION);
        verify(conversations).leave(10, NOW);
    }

    private static Conversation direct(OffsetDateTime myLeftAt) {
        return Conversation.builder().id(CONVERSATION).type(ConversationType.DIRECT).members(List.of(
                ConversationMember.builder().id(10L).userId(ME).joinedAt(NOW.minusDays(3)).leftAt(myLeftAt).build(),
                ConversationMember.builder().id(11L).userId(PARTNER).joinedAt(NOW.minusDays(3)).build())).build();
    }
}
