package com.pickong.common.websocket;

import com.pickong.common.error.ErrorCode;

/**
 * 요청 하나가 업무 규칙에 어긋났을 때 던집니다. 연결은 유지하고 {@code /user/queue/errors}로 오류를 보냅니다.
 * 보내기 오류에는 요청의 clientMessageId를 담아, 프론트가 어떤 말풍선의 실패인지 알 수 있게 합니다 (common 5.9).
 */
public class StompRequestException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String clientMessageId;

    public StompRequestException(ErrorCode errorCode, String clientMessageId) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.clientMessageId = clientMessageId;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }
}
