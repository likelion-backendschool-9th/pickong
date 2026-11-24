package com.pickong.moderation.domain;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.domain.vo.ReportAction;
import com.pickong.moderation.domain.vo.ReportStatus;
import java.util.Set;

/** 신고 처리 요청 규칙. 화면 검증과 별도로 서버에서 다시 확인합니다. */
public final class ReportRules {

    public static final int MEMO_MAX = 200;

    /** 화면의 제재 종류. 지금은 저장하지 않고, 요청이 올바른지만 확인합니다. */
    private static final Set<String> SANCTION_TYPES = Set.of("WARNING", "SUSPEND_7", "SUSPEND_30", "BAN");

    private ReportRules() {
    }

    public static ReportStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ModerationErrorCode.INVALID_REPORT_STATUS);
        }
        try {
            return ReportStatus.valueOf(raw.strip());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ModerationErrorCode.INVALID_REPORT_STATUS);
        }
    }

    public static ReportAction parseAction(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ModerationErrorCode.INVALID_REPORT_ACTION);
        }
        try {
            return ReportAction.valueOf(raw.strip());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ModerationErrorCode.INVALID_REPORT_ACTION);
        }
    }

    /**
     * 제재 종류는 회원 제재 요청에만 있습니다.
     * 다른 액션에 섞여 있으면, 제재가 된 것처럼 무시하지 않고 거절합니다.
     */
    public static void checkSanctionType(ReportAction action, String sanctionType) {
        String value = blankToNull(sanctionType);
        if (action == ReportAction.SANCTION_USER) {
            if (value == null || !SANCTION_TYPES.contains(value)) {
                throw new BusinessException(ModerationErrorCode.INVALID_SANCTION_TYPE);
            }
            return;
        }
        if (value != null) {
            throw new BusinessException(ModerationErrorCode.SANCTION_TYPE_NOT_ALLOWED);
        }
    }

    /**
     * 기각 메모는 비어도 됩니다. 숨김 · 제재 메모는 필수입니다.
     * 비어 있는 기각 메모는 null로 돌려줍니다.
     */
    public static String normalizeMemo(ReportAction action, String memo) {
        String value = blankToNull(memo);
        if (value != null && value.codePointCount(0, value.length()) > MEMO_MAX) {
            throw new BusinessException(ModerationErrorCode.RESOLUTION_MEMO_TOO_LONG);
        }
        if (action != ReportAction.DISMISS && value == null) {
            throw new BusinessException(ModerationErrorCode.RESOLUTION_MEMO_REQUIRED);
        }
        return value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
