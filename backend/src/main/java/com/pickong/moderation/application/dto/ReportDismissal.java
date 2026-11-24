package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.model.Report;

/** 기각 저장 결과. 선택 신고가 대기가 아니면 다른 신고는 바꾸지 않습니다. */
public record ReportDismissal(Kind kind, Report report) {

    public enum Kind {
        MISSING,
        ALREADY_RESOLVED,
        DISMISSED
    }

    public static ReportDismissal missing() {
        return new ReportDismissal(Kind.MISSING, null);
    }

    public static ReportDismissal alreadyResolved() {
        return new ReportDismissal(Kind.ALREADY_RESOLVED, null);
    }

    public static ReportDismissal dismissed(Report report) {
        return new ReportDismissal(Kind.DISMISSED, report);
    }
}
