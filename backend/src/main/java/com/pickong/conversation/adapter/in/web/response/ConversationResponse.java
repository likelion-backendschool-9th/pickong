package com.pickong.conversation.adapter.in.web.response;

import com.pickong.conversation.application.dto.ConversationPartner;
import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.GroupMemberResult;
import com.pickong.conversation.domain.vo.ConversationType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 프론트 ConversationDto (1:1과 그룹).
 *
 * @param title       그룹 이름, 1:1은 null
 * @param partner     1:1 상대, 그룹은 null
 * @param members     그룹의 참여 중인 사람(나 포함, 들어온 순), 1:1은 빈 목록
 * @param memberCount 참여 중인 사람 수 (1:1은 2)
 * @param muted       내 알림 끄기 (값만 저장)
 */
public record ConversationResponse(
        String id,
        ConversationType type,
        String title,
        Partner partner,
        List<Member> members,
        int memberCount,
        MessageResponse lastMessage,
        long unreadCount,
        boolean muted,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * 프론트 UserSummaryDto. 화면은 아이디만 보여 주지만, nickname은 프론트 모양을 맞추려고 이름(없으면 아이디)을 넣습니다.
     */
    public record Partner(UUID id, String username, String nickname, String profileImageUrl) {

        static Partner from(ConversationPartner p) {
            return new Partner(p.publicId(), p.username(), p.name() != null ? p.name() : p.username(), p.profileImageUrl());
        }
    }

    /** 그룹 참여자 (UserSummaryDto + 나인지) */
    public record Member(UUID id, String username, String nickname, String profileImageUrl, boolean isMe) {

        static Member from(GroupMemberResult m) {
            return new Member(m.publicId(), m.username(), m.name() != null ? m.name() : m.username(), m.profileImageUrl(),
                    m.me());
        }
    }

    public static ConversationResponse from(ConversationSummaryResult c) {
        return new ConversationResponse(String.valueOf(c.id()), c.type(), c.title(),
                c.partner() == null ? null : Partner.from(c.partner()),
                c.members().stream().map(Member::from).toList(), c.memberCount(),
                c.lastMessage() == null ? null : MessageResponse.from(c.lastMessage()), c.unreadCount(), c.muted(),
                c.createdAt(), c.updatedAt());
    }
}
