package com.pickong.moderation.application;

import com.pickong.moderation.application.dto.ReportTargetLookup;
import com.pickong.moderation.domain.model.ReportSnapshot;
import com.pickong.moderation.domain.vo.ReportTargetType;

/** 조회한 게시물 · 댓글로 신고 당시 스냅샷을 만듭니다. */
public final class ReportSnapshots {

    private ReportSnapshots() {
    }

    public static ReportSnapshot from(ReportTargetLookup target) {
        if (target == null || (target.targetType() != ReportTargetType.FEED && target.targetType() != ReportTargetType.COMMENT)) {
            throw new IllegalArgumentException("게시물 또는 댓글만 스냅샷을 만듭니다.");
        }
        return ReportSnapshot.full(target.text(), target.authorId());
    }
}
