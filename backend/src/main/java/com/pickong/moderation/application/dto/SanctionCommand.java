package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.vo.SanctionType;
import java.time.Duration;
import com.pickong.common.error.BusinessException;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.model.Sanction;

/** 관리자 id는 인증 주체에서, 원인 신고 id는 신고 처리 UseCase에서만 전달합니다. */
public record SanctionCommand(long userId, SanctionType type, String reason, Duration suspensionDuration,
        long adminId, Long sourceReportId) {
    /** 관리자 웹 표현을 공통 제재 명령으로 변환합니다. 도메인에는 UI Enum을 전달하지 않습니다. */
    public static SanctionCommand fromAdminType(long userId, String type, String reason, long adminId, Long sourceReportId) {
        SanctionType domainType = switch (type == null ? "" : type) {
            case "WARNING" -> SanctionType.WARNING;
            case "SUSPEND_7", "SUSPEND_30" -> SanctionType.SUSPEND;
            case "BAN" -> SanctionType.BAN;
            default -> throw new BusinessException(ModerationErrorCode.INVALID_SANCTION_TYPE);
        };
        Duration duration = switch (type) {
            case "SUSPEND_7" -> Sanction.SEVEN_DAYS;
            case "SUSPEND_30" -> Sanction.THIRTY_DAYS;
            default -> null;
        };
        return new SanctionCommand(userId, domainType, reason, duration, adminId, sourceReportId);
    }
}
