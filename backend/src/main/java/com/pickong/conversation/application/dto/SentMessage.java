package com.pickong.conversation.application.dto;

import java.util.UUID;

/**
 * 저장된 메시지를 실시간으로 보낼 때 쓰는 값. 받는 사람마다 isMine이 달라서 보낸 사람의 내부 id를 함께 둡니다
 * (내부 id는 응답으로 내보내지 않음).
 *
 * @param message 보낸 사람 기준(mine = true)의 메시지
 */
public record SentMessage(MessageResult message, long senderId, UUID clientMessageId) {

    public long id() {
        return message.id();
    }
}
