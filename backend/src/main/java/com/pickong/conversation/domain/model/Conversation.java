package com.pickong.conversation.domain.model;

import com.pickong.conversation.domain.vo.ConversationType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 대화방 애그리거트 루트. DIRECT | GROUP과 참여자(ConversationMember) 목록.
 * <p>DIRECT는 title이 없습니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Conversation {
    private Long id;
    private ConversationType type;
    private String title;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Builder.Default
    private List<ConversationMember> members = new ArrayList<>();

    public List<ConversationMember> getMembers() {
        return List.copyOf(members);
    }
}
