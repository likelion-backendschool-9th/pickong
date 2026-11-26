package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.model.Report;
import java.time.OffsetDateTime;

/** 회원 신고 접수 결과. 스냅샷 원문과 작성자 정보는 넣지 않습니다. */
public record ReportReceipt(
        long id,
        String targetType,
        long targetId,
        String reason,
        String status,
        OffsetDateTime createdAt) {

    public static ReportReceipt from(Report report) {
        return new ReportReceipt(
                report.getId(),
                report.getTargetType().name(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus().name(),
                report.getCreatedAt());
    }
}
