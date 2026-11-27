package com.pickong.common.websocket;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * STOMP 요청(@MessageMapping) 처리 중 난 오류를 보낸 세션의 {@code /user/queue/errors}로 보냅니다. 연결은 유지합니다.
 * HTTP의 GlobalExceptionHandler와 같은 코드 · 문장을 씁니다.
 */
@ControllerAdvice
public class StompExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StompExceptionHandler.class);
    static final String ERRORS = "/queue/errors";

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS, broadcast = false)
    public StompErrorPayload handleRequest(StompRequestException e) {
        return StompErrorPayload.of(e.getErrorCode(), e.getClientMessageId());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS, broadcast = false)
    public StompErrorPayload handleBusiness(BusinessException e) {
        return StompErrorPayload.of(e.getErrorCode(), null);
    }

    /** JSON 형식이 틀리거나 값의 타입이 맞지 않음 */
    @MessageExceptionHandler({MessageConversionException.class, MethodArgumentTypeMismatchException.class})
    @SendToUser(destinations = ERRORS, broadcast = false)
    public StompErrorPayload handleInvalidInput(Exception e) {
        return StompErrorPayload.of(CommonErrorCode.INVALID_INPUT, null);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS, broadcast = false)
    public StompErrorPayload handleUnexpected(Exception e) {
        log.error("STOMP 요청 처리 중 예상하지 못한 오류", e);
        return new StompErrorPayload(500, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다", null);
    }
}
