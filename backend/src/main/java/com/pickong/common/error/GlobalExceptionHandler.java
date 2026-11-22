package com.pickong.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

// 전체 컨트롤러에서 발생하는 예외를 한 곳에서 처리함. 응답 형식은 ErrorResponse {status, code, message}
@RestControllerAdvice
public class GlobalExceptionHandler {

    // BusinessException 발생 시 ErrorCode의 status, code, message로 응답
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    // @Valid 검증 실패 시 400 INVALID_INPUT으로 응답 (필드별 오류 메시지를 이어 붙임)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT, message));
    }
}
