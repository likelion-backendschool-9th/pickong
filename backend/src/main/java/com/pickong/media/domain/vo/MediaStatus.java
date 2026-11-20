package com.pickong.media.domain.vo;

/**
 * 미디어 파일 처리 상태.
<p>값 목록은 확정 전입니다 (backend_blueprint.md 8.7 #2).
 */
public enum MediaStatus {
    PENDING,
    UPLOADED,
    PROCESSED,
    FAILED
}
