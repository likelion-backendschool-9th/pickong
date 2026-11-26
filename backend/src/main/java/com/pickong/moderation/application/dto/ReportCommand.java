package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.vo.ReportTargetType;

/** 회원이 신고를 접수할 때 서비스에 넘기는 값. 본문과 작성자는 클라이언트가 주지 않습니다. */
public record ReportCommand(
        long reporterId,
        ReportTargetType targetType,
        long targetId,
        String reason,
        String detail) {
}
