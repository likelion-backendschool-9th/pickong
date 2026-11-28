package com.pickong.conversation.domain.model;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.vo.ConversationType;
import com.pickong.conversation.domain.vo.DirectKey;
import com.pickong.conversation.domain.vo.GroupTitle;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 대화방 애그리거트 루트. DIRECT | GROUP과 참여자(ConversationMember) 목록.
 * <ul>
 *   <li>DIRECT: title 없음, {@link DirectKey}. 다시 참여하면 그 시각 이후 메시지만 보임</li>
 *   <li>GROUP: 이름 1~20자, 본인 포함 3~10명. 방장 없이 멤버 누구나 이름 변경 · 초대, 내보내기 없음.
 *       초대된 사람(다시 초대 포함)은 이전 메시지도 전부 봄</li>
 * </ul>
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Conversation {
    private Long id;
    private ConversationType type;
    private String title;
    private String directKey;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Builder.Default
    private List<ConversationMember> members = new ArrayList<>();

    /**
     * 두 회원의 새 1:1 대화방. 이미 있는지는 저장소가 {@code directKey}로 확인합니다.
     *
     * @throws BusinessException 400 SELF_CONVERSATION_NOT_ALLOWED — 나와의 대화방
     */
    public static Conversation openDirect(long requesterId, long partnerId, OffsetDateTime now) {
        DirectKey key = DirectKey.of(requesterId, partnerId);
        return Conversation.builder()
                .type(ConversationType.DIRECT)
                .directKey(key.value())
                .createdBy(requesterId)
                .createdAt(now)
                .updatedAt(now)
                .members(List.of(ConversationMember.join(requesterId, now), ConversationMember.join(partnerId, now)))
                .build();
    }

    /** 그룹 인원 (나 포함) */
    public static final int GROUP_MIN = 3;
    public static final int GROUP_MAX = 10;

    /**
     * 새 그룹. 초대할 사람은 중복과 나를 빼고 셉니다.
     *
     * @throws BusinessException 400 GROUP_TOO_SMALL — 나를 빼고 2명 미만, 409 GROUP_FULL — 나 포함 10명 초과
     */
    public static Conversation createGroup(long creatorId, GroupTitle title, Collection<Long> inviteeIds, OffsetDateTime now) {
        Set<Long> others = new LinkedHashSet<>(inviteeIds);
        others.remove(creatorId);
        if (others.size() + 1 < GROUP_MIN) {
            throw new BusinessException(ConversationErrorCode.GROUP_TOO_SMALL);
        }
        if (others.size() + 1 > GROUP_MAX) {
            throw new BusinessException(ConversationErrorCode.GROUP_FULL);
        }
        List<ConversationMember> members = new ArrayList<>();
        members.add(ConversationMember.join(creatorId, now));
        others.forEach(id -> members.add(ConversationMember.join(id, now)));
        return Conversation.builder()
                .type(ConversationType.GROUP)
                .title(title.value())
                .createdBy(creatorId)
                .createdAt(now)
                .updatedAt(now)
                .members(members)
                .build();
    }

    /**
     * 초대할 사람을 나눕니다: 나갔다가 다시 들어오는 사람(기존 행) · 처음 들어오는 사람. 이미 참여 중인 사람은 뺍니다.
     *
     * @throws BusinessException 400 NOT_A_GROUP, 404 CONVERSATION_NOT_FOUND — 초대한 사람이 참여 중이 아님,
     *         409 GROUP_FULL — 초대 후 10명 초과
     */
    public InvitePlan planInvite(long inviterId, Collection<Long> userIds) {
        ensureGroup();
        ensureActiveMember(inviterId);
        List<ConversationMember> rejoining = new ArrayList<>();
        List<Long> joining = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(userIds)) {
            Optional<ConversationMember> existing = memberOf(userId);
            if (existing.isEmpty()) {
                joining.add(userId);
            } else if (!existing.get().isActive()) {
                rejoining.add(existing.get());
            }
        }
        if (activeMemberIds().size() + rejoining.size() + joining.size() > GROUP_MAX) {
            throw new BusinessException(ConversationErrorCode.GROUP_FULL);
        }
        return new InvitePlan(rejoining, joining);
    }

    /**
     * @param rejoining 나갔다가 다시 들어오는 참여자 (기존 행을 다시 참여시킴)
     * @param joining   처음 들어오는 회원 id
     */
    public record InvitePlan(List<ConversationMember> rejoining, List<Long> joining) {
        public boolean isEmpty() {
            return rejoining.isEmpty() && joining.isEmpty();
        }
    }

    /** @throws BusinessException 400 NOT_A_GROUP — 1:1 대화방 */
    public void ensureGroup() {
        if (type != ConversationType.GROUP) {
            throw new BusinessException(ConversationErrorCode.NOT_A_GROUP);
        }
    }

    /**
     * 이 참여자가 볼 수 있는 메시지의 시작 시각. 1:1은 참여 시각부터, 그룹은 처음부터(null)입니다.
     */
    public OffsetDateTime visibleFrom(ConversationMember member) {
        return isDirect() ? member.getJoinedAt() : null;
    }

    public List<ConversationMember> getMembers() {
        return List.copyOf(members);
    }

    public boolean isDirect() {
        return type == ConversationType.DIRECT;
    }

    /**
     * 새 메시지가 오면 다시 참여시킬 참여자. 1:1에서 나간 상대는 새 메시지를 받으면 대화방이 다시 보여야 하므로 다시 참여합니다
     * (다시 참여한 시각 이후의 메시지만 보임). 그룹에서 나간 사람은 다시 넣지 않습니다.
     */
    public List<ConversationMember> membersToRejoinOnNewMessage() {
        return isDirect() ? members.stream().filter(m -> !m.isActive()).toList() : List.of();
    }

    /** 새 메시지를 받을 회원: 참여 중인 회원 + 이번에 다시 참여할 회원 (보낸 사람 포함) */
    public List<Long> recipientsOfNewMessage() {
        return isDirect()
                ? members.stream().map(ConversationMember::getUserId).toList()
                : activeMemberIds();
    }

    /** 지금 참여 중인 회원 (읽음 알림을 받을 회원) */
    public List<Long> activeMemberIds() {
        return members.stream().filter(ConversationMember::isActive).map(ConversationMember::getUserId).toList();
    }

    /** 나간 참여자도 포함합니다. */
    public Optional<ConversationMember> memberOf(long userId) {
        return members.stream().filter(m -> m.getUserId() == userId).findFirst();
    }

    /**
     * 지금 참여 중인 회원의 참여 정보. 나간 대화방은 보내기 · 읽기 · 조회를 할 수 없습니다.
     *
     * @throws BusinessException 404 CONVERSATION_NOT_FOUND — 참여하지 않았거나 나감 (대화방이 있다는 것도 알리지 않음)
     */
    public ConversationMember ensureActiveMember(long userId) {
        return memberOf(userId)
                .filter(ConversationMember::isActive)
                .orElseThrow(() -> new BusinessException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
    }
}
