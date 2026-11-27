package com.pickong.conversation.adapter.in.websocket.payload;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;

/** STOMP 본문의 문자열 ID를 내부 id로 바꿉니다 (JSON의 ID는 문자열, common 5.4). */
final class PayloadIds {

    private PayloadIds() {
    }

    /** @throws BusinessException 400 INVALID_INPUT — 없거나 양의 정수가 아님 */
    static long positiveLong(String value) {
        try {
            if (value != null) {
                long id = Long.parseLong(value);
                if (id > 0) {
                    return id;
                }
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 INVALID_INPUT
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
}
