package com.pickong.conversation.domain.model;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.domain.ConversationErrorCode;
import com.pickong.conversation.domain.vo.ConversationType;
import com.pickong.conversation.domain.vo.DirectKey;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 대화방 애그리거트 루트. DIRECT | GROUP과 참여자(ConversationMember) 목록.
 * <p>DIRECT는 title이 없고 {@link DirectKey}가 있습니다. 지금은 DIRECT만 만들 수 있습니다(그룹은 별도 이슈).
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
