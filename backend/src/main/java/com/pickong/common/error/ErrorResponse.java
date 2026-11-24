package com.pickong.common.error;

/**
 * 오류 응답 본문: {@code { "status": 404, "code": "FEED_NOT_FOUND", "message": "사용자에게 보여 줄 문장" }}
 * <p>
 * blueprint의 기준 형식은 {@code {code, message}}이고, 기존 응답 · 테스트와 맞추기 위해 status를 함께 둡니다.
 */
public record ErrorResponse(int status, String code, String message) {

    /** 오류 코드의 기본 메시지로 응답 본문을 만듭니다. */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage());
    }

    /** 메시지만 바꿔서 만듭니다. (예: 입력 검증 실패 시 필드별 오류 문구를 이어 붙인 메시지) */
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getStatus(), errorCode.getCode(), message);
    }
}
