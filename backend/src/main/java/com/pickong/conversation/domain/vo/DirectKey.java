package com.pickong.conversation.domain.vo;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.domain.ConversationErrorCode;

/**
 * 1:1 대화방 중복 방지 키 "작은 회원 id:큰 회원 id". 누가 먼저 열든 같은 두 사람이면 같은 값입니다.
 * DB의 {@code conversations.direct_key} UNIQUE가 동시에 여는 경우도 하나로 막습니다.
 */
public record DirectKey(String value) {

    /** @throws BusinessException 400 SELF_CONVERSATION_NOT_ALLOWED — 두 회원이 같을 때 */
    public static DirectKey of(long userId, long otherUserId) {
        if (userId == otherUserId) {
            throw new BusinessException(ConversationErrorCode.SELF_CONVERSATION_NOT_ALLOWED);
        }
        return new DirectKey(Math.min(userId, otherUserId) + ":" + Math.max(userId, otherUserId));
    }
}
