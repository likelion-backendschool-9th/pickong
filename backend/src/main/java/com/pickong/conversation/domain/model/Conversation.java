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
