package com.pickong.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.vo.ConversationType;
import com.pickong.conversation.domain.vo.DirectKey;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConversationRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-28T03:00:00Z");

    @Test
    @DisplayName("1:1 키는 누가 먼저 열든 '작은 id:큰 id'")
    void directKeyIsOrderIndependent() {
        assertThat(DirectKey.of(7, 3)).isEqualTo(DirectKey.of(3, 7));
        assertThat(DirectKey.of(7, 3).value()).isEqualTo("3:7");
    }

    @Test
    @DisplayName("나와의 1:1 대화방은 만들 수 없다")
    void cannotOpenWithSelf() {
        assertThatThrownBy(() -> Conversation.openDirect(5, 5, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ConversationErrorCode.SELF_CONVERSATION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("새 1:1 대화방: DIRECT, 키, 두 사람 모두 지금 참여")
    void openDirect() {
        Conversation c = Conversation.openDirect(9, 2, NOW);

        assertThat(c.getType()).isEqualTo(ConversationType.DIRECT);
        assertThat(c.getDirectKey()).isEqualTo("2:9");
        assertThat(c.getCreatedBy()).isEqualTo(9);
        assertThat(c.getMembers()).extracting(ConversationMember::getUserId).containsExactly(9L, 2L);
        assertThat(c.getMembers()).allSatisfy(m -> {
            assertThat(m.isActive()).isTrue();
            assertThat(m.getJoinedAt()).isEqualTo(NOW);
            assertThat(m.getLastReadMessageId()).isNull();
        });
    }

    @Test
    @DisplayName("참여하지 않았거나 나간 회원은 대화방이 없는 것처럼 404")
    void ensureActiveMember() {
        Conversation c = Conversation.builder()
                .members(java.util.List.of(ConversationMember.join(1, NOW), ConversationMember.join(2, NOW).leave(NOW)))
                .build();

        assertThat(c.ensureActiveMember(1).getUserId()).isEqualTo(1);
        assertThatThrownBy(() -> c.ensureActiveMember(2)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ConversationErrorCode.CONVERSATION_NOT_FOUND);
        assertThatThrownBy(() -> c.ensureActiveMember(3)).isInstanceOf(BusinessException.class);
        assertThat(c.memberOf(2)).isPresent();
    }

    @Test
    @DisplayName("나가기 → 다시 참여하면 참여 시각이 바뀌고, 참여 중일 때 다시 참여해도 그대로")
    void leaveAndRejoin() {
        ConversationMember joined = ConversationMember.join(1, NOW);
        ConversationMember left = joined.leave(NOW.plusHours(1));
        ConversationMember back = left.rejoin(NOW.plusHours(2));

        assertThat(left.isActive()).isFalse();
        assertThat(left.leave(NOW.plusHours(3))).isSameAs(left);
        assertThat(back.isActive()).isTrue();
        assertThat(back.getJoinedAt()).isEqualTo(NOW.plusHours(2));
        assertThat(joined.rejoin(NOW.plusHours(5))).isSameAs(joined);
    }

    @Test
    @DisplayName("읽음 위치는 뒤로 가지 않는다")
    void readPositionOnlyMovesForward() {
        ConversationMember read10 = ConversationMember.join(1, NOW).readUpTo(10);

        assertThat(read10.getLastReadMessageId()).isEqualTo(10);
        assertThat(read10.readUpTo(10)).isSameAs(read10);
        assertThat(read10.readUpTo(4)).isSameAs(read10);
        assertThat(read10.readUpTo(11).getLastReadMessageId()).isEqualTo(11);
    }
}
