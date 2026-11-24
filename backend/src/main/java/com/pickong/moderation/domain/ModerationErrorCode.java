package com.pickong.moderation.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 신고 처리 오류. 화면은 message를 그대로 표시합니다. */
@Getter
public enum ModerationErrorCode implements ErrorCode {

    REPORT_NOT_FOUND(404, "신고를 찾을 수 없어요."),
    /** 대기 상태가 아닌 신고를 다시 처리함 */
    ALREADY_RESOLVED(409, "이미 처리한 신고예요."),
    INVALID_REPORT_STATUS(400, "신고 상태를 확인해 주세요."),
    INVALID_REPORT_ACTION(400, "처리 방법을 골라 주세요."),
    RESOLUTION_MEMO_REQUIRED(400, "처리 사유를 입력해 주세요."),
    RESOLUTION_MEMO_TOO_LONG(400, "처리 사유는 200자까지 쓸 수 있어요."),
    INVALID_SANCTION_TYPE(400, "제재 종류를 확인해 주세요."),
    /** 기각 · 숨김 요청에 제재 종류가 섞여 있음 */
    SANCTION_TYPE_NOT_ALLOWED(400, "제재 종류는 회원 제재에서만 보내 주세요."),
    /**
     * 숨김 · 제재는 대상 모듈 기능이 없어 실행하지 않습니다.
     * 공통 오류 코드에는 미구현 작업용 코드가 없어 501을 씁니다.
     */
    REPORT_ACTION_NOT_SUPPORTED(501, "콘텐츠 숨김과 회원 제재는 아직 처리할 수 없어요.");

    private final int status;
    private final String message;

    ModerationErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
