package com.pickong.placeimport.domain.vo;

/**
 * 장소 가져오기 작업 상태.
<p>값 목록은 확정 전입니다 (backend_blueprint.md 8.7 #2).
 */
public enum ImportJobStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED
}
