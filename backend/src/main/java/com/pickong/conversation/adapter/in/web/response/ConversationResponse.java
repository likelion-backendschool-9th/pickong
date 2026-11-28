package com.pickong.conversation.adapter.in.web.response;

import com.pickong.conversation.application.dto.ConversationPartner;
import com.pickong.conversation.application.dto.ConversationSummaryResult;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 프론트 ConversationDto (1:1 대화방). */
public record ConversationResponse(
        String id,
        Partner partner,
        MessageResponse lastMessage,
        long unreadCount,
        OffsetDateTime updatedAt) {

    /**
     * 프론트 UserSummaryDto. 화면은 아이디만 보여 주지만, nickname은 프론트 모양을 맞추려고 이름(없으면 아이디)을 넣습니다.
     */
    public record Partner(UUID id, String username, String nickname, String profileImageUrl) {

        static Partner from(ConversationPartner p) {
            return new Partner(p.publicId(), p.username(), p.name() != null ? p.name() : p.username(), p.profileImageUrl());
        }
    }

    public static ConversationResponse from(ConversationSummaryResult c) {
        return new ConversationResponse(String.valueOf(c.id()), Partner.from(c.partner()),
                c.lastMessage() == null ? null : MessageResponse.from(c.lastMessage()), c.unreadCount(), c.updatedAt());
    }
}
