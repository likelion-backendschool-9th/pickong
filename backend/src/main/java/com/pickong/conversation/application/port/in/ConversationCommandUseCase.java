package com.pickong.conversation.application.port.in;

import com.pickong.conversation.application.dto.ConversationSummaryResult;
import com.pickong.conversation.application.dto.OpenDirectResult;
import com.pickong.conversation.application.dto.UpdateConversationCommand;
import java.util.List;

/**
 * 대화방 열기 · 만들기 · 바꾸기 · 초대 · 나가기 (blueprint 3장 conversation).
 * 그룹은 방장이 없어 멤버 누구나 이름 변경 · 초대할 수 있고, 내보내기는 없습니다.
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
     * 그룹을 만듭니다. 나를 포함해 3~10명이고, 만든 뒤 '만들기' 안내 메시지를 보냅니다.
     *
     * @param usernames 초대할 사람의 아이디 (대소문자 무시, 나 · 중복은 뺌)
     * @throws com.pickong.common.error.BusinessException 400 INVALID_GROUP_TITLE, 404 USER_NOT_FOUND — 없거나 ACTIVE가 아닌 아이디,
     *         400 GROUP_TOO_SMALL, 409 GROUP_FULL
     */
    ConversationSummaryResult createGroup(long userId, String title, List<String> usernames);

    /**
     * 그룹에 초대합니다(멤버 누구나). 나갔던 사람도 다시 들어오고, 초대된 사람은 이전 대화도 전부 봅니다.
     * 이미 참여 중인 사람은 건너뛰고, 새로 들어온 사람이 있으면 '초대' 안내 메시지를 보냅니다.
     *
     * @throws com.pickong.common.error.BusinessException 404 CONVERSATION_NOT_FOUND, 400 NOT_A_GROUP,
     *         404 USER_NOT_FOUND, 409 GROUP_FULL
     */
    ConversationSummaryResult invite(long userId, long conversationId, List<String> usernames);

    /**
     * 그룹 이름(그룹만, 멤버 누구나)과 내 알림 끄기를 바꿉니다. 이름이 실제로 바뀌면 '이름 변경' 안내 메시지를 보냅니다.
     *
     * @throws com.pickong.common.error.BusinessException 404 CONVERSATION_NOT_FOUND, 400 NOT_A_GROUP · INVALID_GROUP_TITLE
     */
    ConversationSummaryResult update(long userId, long conversationId, UpdateConversationCommand command);

    /**
     * 대화방에서 나갑니다. 나간 뒤에는 목록 · 조회 · 읽음에서 404로 보입니다. 그룹이면 '나가기' 안내 메시지를 보냅니다.
     *
     * @throws com.pickong.common.error.BusinessException 404 CONVERSATION_NOT_FOUND — 참여 중이 아님
     */
    void leave(long userId, long conversationId);
}
