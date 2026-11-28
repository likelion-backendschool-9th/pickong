package com.pickong.conversation.application.dto;

import com.pickong.conversation.domain.vo.MessageType;
import com.pickong.conversation.domain.vo.SystemEvent;
import java.time.OffsetDateTime;

/**
 * 메시지 한 건.
 *
 * @param sender 보낸 사람 (SYSTEM이면 그 일을 한 사람). 응답에는 공개 id를 씁니다
 * @param mine   조회한 회원이 보낸 메시지인지
 * @param text   TEXT의 본문. SYSTEM이면 null
 * @param system SYSTEM의 안내 내용. TEXT면 null
 */
public record MessageResult(long id, long conversationId, ConversationPartner sender, boolean mine, MessageType type,
                            String text, SystemEvent system, OffsetDateTime createdAt) {

    /** 받는 사람마다 isMine이 달라서 실시간 전달 때 바꿔 씁니다. */
    public MessageResult withMine(boolean value) {
        return new MessageResult(id, conversationId, sender, value, type, text, system, createdAt);
    }
}
