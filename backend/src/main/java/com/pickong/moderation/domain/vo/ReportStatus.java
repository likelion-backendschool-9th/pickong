package com.pickong.moderation.domain.vo;

/**
 * 신고 처리 상태.
<p>값 목록은 확정 전입니다 (backend_blueprint.md 8.7 #2).
 */
public enum ReportStatus {
    PENDING,
    HANDLED,
    DISMISSED
}
