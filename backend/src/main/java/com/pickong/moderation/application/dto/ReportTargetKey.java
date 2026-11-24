package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.vo.ReportTargetType;

/** 신고 대상 한 건. 목록에서 같은 대상을 한 번만 조회할 때 키로 씁니다. */
public record ReportTargetKey(ReportTargetType type, long id) {
}
