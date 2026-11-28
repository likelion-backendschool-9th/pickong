package com.pickong.conversation.adapter.in.web.response;

import com.pickong.conversation.application.dto.ConversationPartner;
import com.pickong.conversation.application.dto.MessageResult;
import com.pickong.conversation.domain.vo.MessageType;
import com.pickong.conversation.domain.vo.SystemEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 프론트 MessageDto. ID는 문자열(common 5.4), 보낸 사람은 공개 id(users.public_id)입니다.
 *
 * @param type       TEXT(회원이 보낸 글) | SYSTEM(그룹 만들기 · 초대 · 나가기 · 이름 변경 안내)
 * @param sender     보낸 사람 (SYSTEM이면 그 일을 한 사람). 그룹에서 말풍선마다 아이디 · 사진을 보여 줄 때 씁니다
 * @param text       TEXT의 본문, SYSTEM이면 null
 * @param system     SYSTEM의 안내 내용, TEXT면 null
 * @param attachment 첨부. 첨부를 만들 때까지 항상 null
 */
public record MessageResponse(
        String id,
        String conversationId,
        UUID senderId,
        Sender sender,
        boolean isMine,
        MessageType type,
        String text,
        SystemEvent system,
        Object attachment,
        OffsetDateTime createdAt) {

    /** 프론트 UserSummaryDto. nickname은 이름(없으면 아이디) */
    public record Sender(UUID id, String username, String nickname, String profileImageUrl) {

        public static Sender from(ConversationPartner p) {
            return new Sender(p.publicId(), p.username(), p.name() != null ? p.name() : p.username(), p.profileImageUrl());
        }
    }

    public static MessageResponse from(MessageResult m) {
        return new MessageResponse(String.valueOf(m.id()), String.valueOf(m.conversationId()), m.sender().publicId(),
                Sender.from(m.sender()), m.mine(), m.type(), m.text(), m.system(), null, m.createdAt());
    }
}
