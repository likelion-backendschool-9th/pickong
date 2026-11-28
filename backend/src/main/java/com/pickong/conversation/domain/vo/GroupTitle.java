package com.pickong.conversation.domain.vo;

import com.pickong.common.error.BusinessException;
import com.pickong.conversation.domain.ConversationErrorCode;

/** 그룹 이름: 앞뒤 공백을 지우고 1~20자(코드 포인트, 한글 · 이모지 한 글자 = 1). DB의 char_length 제한과 같음 */
public record GroupTitle(String value) {

    public static final int MAX = 20;

    /** @throws BusinessException 400 INVALID_GROUP_TITLE */
    public static GroupTitle of(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > MAX) {
            throw new BusinessException(ConversationErrorCode.INVALID_GROUP_TITLE);
        }
        return new GroupTitle(value);
    }
}
