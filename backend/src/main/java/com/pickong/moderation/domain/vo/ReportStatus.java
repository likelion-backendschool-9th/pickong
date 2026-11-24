package com.pickong.moderation.domain.vo;

/**
 * 신고 처리 상태. 처리 액션(기각 · 숨김 · 제재)과 섞지 않습니다.
 * <p>화면과 같습니다. 대기만 처리할 수 있고, 완료를 다시 대기로 되돌리지 않습니다.
 */
public enum ReportStatus {
    PENDING,
    RESOLVED
}
