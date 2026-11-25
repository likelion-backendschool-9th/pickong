package com.pickong.moderation.adapter.in.web.admin;

import com.pickong.moderation.application.dto.SanctionCommand;

/** 직접 제재 요청. 관리자와 원인 신고 정보는 본문에서 받지 않습니다. */
public record CreateSanctionRequest(String type, String reason) {
    public SanctionCommand toCommand(long userId, long adminId) {
        return SanctionCommand.fromAdminType(userId, type, reason, adminId, null);
    }
}
