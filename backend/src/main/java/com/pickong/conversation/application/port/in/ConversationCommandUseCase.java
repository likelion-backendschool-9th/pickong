package com.pickong.conversation.application.port.in;

import com.pickong.conversation.application.dto.OpenDirectResult;

/**
 * 대화방 열기 · 나가기. (blueprint 3장: POST /conversations/direct, DELETE /conversations/{id}/members/me)
 * 그룹 만들기는 별도 이슈입니다.
 */
public interface ConversationCommandUseCase {

    /**
     * 상대와의 1:1 대화방을 엽니다. 이미 있으면 그 대화방을 돌려주고, 내가 나갔던 대화방이면 다시 참여합니다
     * (다시 참여한 시각 이후의 메시지만 보입니다). 동시에 열어도 대화방은 하나만 생깁니다.
     *
     * @throws com.pickong.common.error.BusinessException 404 USER_NOT_FOUND — 상대가 없거나 정상(ACTIVE) 회원이 아님,
     *         400 SELF_CONVERSATION_NOT_ALLOWED — 나 자신
     */
    OpenDirectResult openDirect(long userId, String partnerUsername);

    /**
     * 대화방에서 나갑니다. 나간 뒤에는 목록 · 조회 · 읽음에서 404로 보입니다.
     *
     * @throws com.pickong.common.error.BusinessException 404 CONVERSATION_NOT_FOUND — 참여 중이 아님
     */
    void leave(long userId, long conversationId);
}
