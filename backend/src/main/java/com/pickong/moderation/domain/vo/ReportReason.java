package com.pickong.moderation.domain.vo;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.domain.ModerationErrorCode;

/**
 * 회원 신고 접수의 사유. 저장된 {@code reports.reason}은 문자열이라
 * 이 목록에 없는 기존 값도 관리자 조회를 막지 않습니다.
 */
public enum ReportReason {
    SPAM,
    ABUSE,
    IMPERSONATION,
    COPYRIGHT,
    INAPPROPRIATE,
    ETC;

    public static ReportReason parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ModerationErrorCode.INVALID_REPORT_REASON);
        }
        try {
            return ReportReason.valueOf(raw.strip());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ModerationErrorCode.INVALID_REPORT_REASON);
        }
    }
}
