package com.pickong.conversation.application.port.in;

/**
 * 메시지 읽음 처리. HTTP(PATCH /conversations/{id}/read)와 STOMP(/app/dm.read)가 같이 씁니다.
 * 메시지 보내기(send)는 STOMP 이슈에서 이 유스케이스에 추가합니다. HTTP 보내기 API는 두지 않습니다.
 */
public interface MessageUseCase {

    /**
     * 읽음 위치를 옮깁니다. 이미 더 뒤까지 읽었으면 바꾸지 않습니다(뒤로 가지 않음).
     *
     * @param lastReadMessageId 여기까지 읽음. null이면 지금 볼 수 있는 마지막 메시지까지
     * @throws com.pickong.common.error.BusinessException 404 CONVERSATION_NOT_FOUND — 없거나 참여 중이 아님,
     *         404 MESSAGE_NOT_FOUND — 이 대화방에서 내가 볼 수 있는 메시지가 아님
     */
    void markRead(long userId, long conversationId, Long lastReadMessageId);
}
