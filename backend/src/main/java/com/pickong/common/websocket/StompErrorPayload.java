package com.pickong.common.websocket;

import com.pickong.common.error.ErrorCode;

/**
 * {@code /user/queue/errors}로 보내는 오류. status · code · message는 HTTP 오류 응답(common 5.2)과 같은 값입니다.
 *
 * @param clientMessageId 보내기 오류일 때 요청의 clientMessageId, 그 밖에는 null
 */
public record StompErrorPayload(int status, String code, String message, String clientMessageId) {

    public static StompErrorPayload of(ErrorCode errorCode, String clientMessageId) {
        return new StompErrorPayload(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(), clientMessageId);
    }
}
