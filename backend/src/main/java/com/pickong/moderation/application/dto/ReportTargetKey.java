package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.vo.ReportTargetType;

/** 신고 대상 한 건. 현재 콘텐츠를 대상마다 한 번만 조회할 때 키로 씁니다. 미리보기 결과의 키는 신고 id입니다. */
public record ReportTargetKey(ReportTargetType type, long id) {
}
