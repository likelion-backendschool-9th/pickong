package com.pickong.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.model.Message;
import com.pickong.conversation.domain.vo.ConversationType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MessageRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-28T03:00:00Z");
    private static final UUID CLIENT_ID = UUID.randomUUID();

    @Test
    @DisplayName("본문은 받은 그대로 저장 (앞뒤 공백 · 줄바꿈 유지)")
    void keepsTextAsIs() {
        Message m = Message.write(1, 2, CLIENT_ID, "  안녕\n반가워 ", NOW);

        assertThat(m.getContent()).isEqualTo("  안녕\n반가워 ");
        assertThat(m.getConversationId()).isEqualTo(1);
        assertThat(m.getSenderId()).isEqualTo(2);
        assertThat(m.getClientMessageId()).isEqualTo(CLIENT_ID);
        assertThat(m.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("비었거나 공백뿐이면 EMPTY_MESSAGE")
    void rejectsBlank(String text) {
        assertThatThrownBy(() -> Message.write(1, 2, CLIENT_ID, text, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ConversationErrorCode.EMPTY_MESSAGE);
    }

    @Test
    @DisplayName("2,000자는 코드 포인트로 센다: 이모지 2,000개는 되고 2,001개는 MESSAGE_TOO_LONG")
    void lengthInCodePoints() {
        String emoji = "😀";
        assertThat(Message.write(1, 2, CLIENT_ID, emoji.repeat(2_000), NOW).getContent()).hasSize(4_000);
        assertThatThrownBy(() -> Message.write(1, 2, CLIENT_ID, emoji.repeat(2_001), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ConversationErrorCode.MESSAGE_TOO_LONG);
    }

    @Test
    @DisplayName("1:1: 새 메시지가 오면 나간 상대도 다시 참여하고 받는다")
    void directRejoinsLeftPartner() {
        ConversationMember me = ConversationMember.join(1, NOW);
        ConversationMember partner = ConversationMember.join(2, NOW).leave(NOW);
        Conversation direct = Conversation.builder().type(ConversationType.DIRECT).members(List.of(me, partner)).build();

        assertThat(direct.membersToRejoinOnNewMessage()).containsExactly(partner);
        assertThat(direct.recipientsOfNewMessage()).containsExactly(1L, 2L);
        assertThat(direct.activeMemberIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("그룹: 나간 사람은 다시 넣지 않고 받지도 않는다")
    void groupKeepsLeftMembersOut() {
        Conversation group = Conversation.builder().type(ConversationType.GROUP)
                .members(List.of(ConversationMember.join(1, NOW), ConversationMember.join(2, NOW).leave(NOW),
                        ConversationMember.join(3, NOW)))
                .build();

        assertThat(group.membersToRejoinOnNewMessage()).isEmpty();
        assertThat(group.recipientsOfNewMessage()).containsExactly(1L, 3L);
    }
}
