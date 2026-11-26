package com.pickong.moderation.adapter.in.web.response;

import com.pickong.moderation.application.dto.ReportReceipt;
import java.time.OffsetDateTime;

/** 회원 신고 접수 결과. 스냅샷과 작성자 정보는 넣지 않습니다. 게시물은 화면 값 POST입니다. */
public record CreateReportResponse(
        long id,
        String targetType,
        long targetId,
        String reason,
        String status,
        OffsetDateTime createdAt) {

    public static CreateReportResponse from(ReportReceipt receipt) {
        String targetType = "FEED".equals(receipt.targetType()) ? "POST" : receipt.targetType();
        return new CreateReportResponse(
                receipt.id(), targetType, receipt.targetId(), receipt.reason(), receipt.status(), receipt.createdAt());
    }
}
