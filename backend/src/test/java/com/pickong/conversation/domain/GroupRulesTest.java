package com.pickong.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.domain.model.Conversation;
import com.pickong.conversation.domain.model.ConversationMember;
import com.pickong.conversation.domain.vo.ConversationType;
import com.pickong.conversation.domain.vo.GroupTitle;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 그룹 규칙: 방장 없음, 나 포함 3~10명, 이름 1~20자, 초대된 사람은 이전 대화도 봄 */
class GroupRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-28T03:00:00Z");

    @Test
    @DisplayName("이름은 앞뒤 공백을 지우고 1~20자(이모지 한 개 = 1자)")
    void title() {
        assertThat(GroupTitle.of("  모임 ").value()).isEqualTo("모임");
        assertThat(GroupTitle.of("😀".repeat(20)).value()).hasSize(40);
        for (String bad : new String[] {null, "", "   ", "가".repeat(21)}) {
            assertThatThrownBy(() -> GroupTitle.of(bad)).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ConversationErrorCode.INVALID_GROUP_TITLE);
        }
    }

    @Test
    @DisplayName("만들기: 방장 없이 만든 사람 먼저, 초대한 순서대로 참여")
    void create() {
        Conversation group = Conversation.createGroup(1, GroupTitle.of("모임"), List.of(3L, 2L, 3L, 1L), NOW);

        assertThat(group.getType()).isEqualTo(ConversationType.GROUP);
        assertThat(group.getDirectKey()).isNull();
        assertThat(group.getMembers()).extracting(ConversationMember::getUserId).containsExactly(1L, 3L, 2L);
    }

    @Test
    @DisplayName("보이는 메시지: 1:1은 참여 시각부터, 그룹은 처음부터(null)")
    void visibleFrom() {
        ConversationMember member = ConversationMember.join(1, NOW);
        Conversation direct = Conversation.builder().type(ConversationType.DIRECT).members(List.of(member)).build();
        Conversation group = Conversation.builder().type(ConversationType.GROUP).members(List.of(member)).build();

        assertThat(direct.visibleFrom(member)).isEqualTo(NOW);
        assertThat(group.visibleFrom(member)).isNull();
    }

    @Test
    @DisplayName("초대: 참여 중인 사람은 빼고, 나갔던 사람은 다시 참여, 처음 오는 사람은 새로 참여")
    void planInvite() {
        Conversation group = Conversation.builder().type(ConversationType.GROUP).members(List.of(
                ConversationMember.join(1, NOW), ConversationMember.join(2, NOW), ConversationMember.join(3, NOW).leave(NOW)))
                .build();

        Conversation.InvitePlan plan = group.planInvite(2, List.of(1L, 3L, 4L, 4L));

        assertThat(plan.rejoining()).extracting(ConversationMember::getUserId).containsExactly(3L);
        assertThat(plan.joining()).containsExactly(4L);
        assertThat(group.planInvite(1, List.of(2L)).isEmpty()).isTrue();
    }
}
