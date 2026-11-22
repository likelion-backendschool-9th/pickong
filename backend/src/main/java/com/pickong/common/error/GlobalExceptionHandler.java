package com.pickong.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// 전체 컨트롤러에서 발생하는 예외를 한 곳에서 처리함
@RestControllerAdvice
public class GlobalExceptionHandler {

    // BusinessException 발생 시 ErrorCode의 status, message로 응답
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(Map.of(
                        "status", errorCode.getStatus(),
                        "message", errorCode.getMessage()
                ));
    }
}
