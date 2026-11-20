package com.pickong.moderation.domain.vo;

/**
 * 신고 대상 종류.
<p>값 목록은 확정 전입니다 (backend_blueprint.md 8.7 #2).
 */
public enum ReportTargetType {
    FEED,
    COMMENT,
    USER,
    COLLECTION
}
