package com.pickong.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

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

    // @Valid 검증 실패 시 400으로 응답 (필드별 오류 메시지 포함)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(400)
                .body(Map.of(
                        "status", 400,
                        "message", message
                ));
    }
}
