package com.pickong.moderation.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 신고·제재 처리 오류. 화면은 message를 그대로 표시합니다. */
@Getter
public enum ModerationErrorCode implements ErrorCode {

    REPORT_NOT_FOUND(404, "신고를 찾을 수 없어요."),
    /** 대기 상태가 아닌 신고를 다시 처리함 */
    ALREADY_RESOLVED(409, "이미 처리한 신고예요."),
    /** 같은 회원이 같은 대상을 이미 대기로 신고함 */
    DUPLICATE_REPORT(409, "이미 대기 중인 신고가 있어요."),
    INVALID_REPORT_REASON(400, "신고 사유를 골라 주세요."),
    INVALID_REPORT_TARGET(400, "게시물이나 댓글만 신고할 수 있어요."),
    REPORT_DETAIL_REQUIRED(400, "신고 내용을 입력해 주세요."),
    REPORT_DETAIL_TOO_LONG(400, "신고 내용은 500자까지 쓸 수 있어요."),
    SELF_REPORT(400, "내가 쓴 글은 신고할 수 없어요."),
    /** 없거나 볼 수 없는 대상. 두 경우를 나누지 않습니다. */
    REPORT_TARGET_NOT_FOUND(404, "신고할 대상을 찾을 수 없어요."),
    INVALID_REPORT_STATUS(400, "신고 상태를 확인해 주세요."),
    INVALID_REPORT_ACTION(400, "처리 방법을 골라 주세요."),
    REPORT_TARGET_ACTION_NOT_ALLOWED(400, "이 신고 대상에는 해당 처리를 적용할 수 없어요."),
    RESOLUTION_MEMO_REQUIRED(400, "처리 사유를 입력해 주세요."),
    RESOLUTION_MEMO_TOO_LONG(400, "처리 사유는 200자까지 쓸 수 있어요."),
    INVALID_SANCTION_TYPE(400, "제재 종류를 확인해 주세요."),
    SANCTION_NOT_FOUND(404, "해당 사용자의 제재를 찾을 수 없어요."),
    INVALID_SANCTION_PERIOD(400, "정지 기간은 7일 또는 30일이어야 해요."),
    SANCTION_REASON_REQUIRED(400, "제재 사유를 입력해 주세요."),
    SANCTION_REASON_TOO_LONG(400, "사유는 200자까지 쓸 수 있어요."),
    SANCTION_ALREADY_ENDED(409, "이미 종료된 제재예요."),
    SANCTION_ALREADY_EXPIRED(409, "이미 만료된 제재예요. 만료 처리가 필요해요."),
    INVALID_SANCTION_TRANSITION(409, "변경할 수 없는 제재 상태예요."),
    ACTIVE_SANCTION_EXISTS(409, "종료되지 않은 이용 제한 제재가 있어요."),
    DUPLICATE_SANCTION_SOURCE(409, "이 신고로 이미 제재가 등록되었어요."),
    /** 기각 · 숨김 요청에 제재 종류가 섞여 있음 */
    SANCTION_TYPE_NOT_ALLOWED(400, "제재 종류는 회원 제재에서만 보내 주세요."),
    /**
     * 컬렉션 관련 처리는 대상 모듈의 기반 구현 전까지 실행하지 않습니다.
     * 공통 오류 코드에는 미구현 작업용 코드가 없어 501을 씁니다.
     */
    REPORT_ACTION_NOT_SUPPORTED(501, "이 신고 대상의 처리는 아직 지원하지 않아요.");

    private final int status;
    private final String message;

    ModerationErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
