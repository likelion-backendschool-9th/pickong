package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.vo.SanctionType;
import java.time.OffsetDateTime;
import java.util.List;

/** 사용자 제재 화면의 읽기 모델. 계정 상태와 현재 유효한 제재는 별개입니다. */
public record AdminUserView(
        long id,
        String username,
        String nickname,
        String profileImageUrl,
        long reportCount,
        long postCount,
        String status,
        List<ActiveSanction> sanctions) {

    public AdminUserView {
        sanctions = List.copyOf(sanctions);
    }

    public record ActiveSanction(
            long id, SanctionType type, String reason, OffsetDateTime startedAt, OffsetDateTime until) {
    }
}
