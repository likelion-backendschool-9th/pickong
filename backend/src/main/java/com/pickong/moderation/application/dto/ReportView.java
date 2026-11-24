package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.model.Report;

/** 관리자 목록 한 줄. 신고와, 따로 조회한 미리보기 · 회원 표시 정보입니다. */
public record ReportView(
        Report report,
        ReportTargetPreview target,
        ReportPartyView reportedUser,
        ReportPartyView reporter) {
}
