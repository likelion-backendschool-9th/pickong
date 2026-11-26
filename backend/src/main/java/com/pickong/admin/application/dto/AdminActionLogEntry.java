package com.pickong.admin.application.dto;

import java.time.OffsetDateTime;

/** 추가 전용 작업 로그 입력. targetId는 소유 모듈 내부 id이며 상세는 TEXT 그대로 보존합니다. */
public record AdminActionLogEntry(long adminId, String actionType, String targetType, Long targetId,
        String reason, String detail, Long sourceReportId, OffsetDateTime createdAt) {
}
