package com.pickong.conversation.application.port.in;

import com.pickong.conversation.application.dto.SendMessageCommand;

/**
 * 메시지 보내기 · 읽음. 보내기는 STOMP(/app/dm.send)만 받고 HTTP 보내기 API는 두지 않습니다.
 * 읽음은 HTTP(PATCH /conversations/{id}/read)와 STOMP(/app/dm.read)가 같이 씁니다.
 */
public interface MessageUseCase {

    /**
     * 메시지를 저장하고 커밋 후 참여자에게 MESSAGE_CREATED를 보냅니다. 같은 clientMessageId로 다시 오면 저장하지 않고
     * 보낸 사람에게만 기존 메시지로 다시 확인합니다. 1:1에서 상대가 나갔으면 다시 참여시킵니다.
     *
     * @throws com.pickong.common.error.BusinessException 404 CONVERSATION_NOT_FOUND — 없거나 참여 중이 아님,
     *         400 EMPTY_MESSAGE · MESSAGE_TOO_LONG, 400 INVALID_INPUT — clientMessageId를 다른 대화방에서 이미 씀
     */
    void send(SendMessageCommand command);

    /**
     * 읽음 위치를 옮깁니다. 이미 더 뒤까지 읽었으면 바꾸지 않습니다(뒤로 가지 않음). 실제로 옮겼으면 커밋 후
     * 참여 중인 회원(내 다른 탭 포함)에게 READ_UPDATED를 보냅니다.
     *
     * @param lastReadMessageId 여기까지 읽음. null이면 지금 볼 수 있는 마지막 메시지까지
     * @throws com.pickong.common.error.BusinessException 404 CONVERSATION_NOT_FOUND — 없거나 참여 중이 아님,
     *         404 MESSAGE_NOT_FOUND — 이 대화방에서 내가 볼 수 있는 메시지가 아님
     */
    void markRead(long userId, long conversationId, Long lastReadMessageId);
}
