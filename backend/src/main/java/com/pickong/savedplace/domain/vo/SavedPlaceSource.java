package com.pickong.savedplace.domain.vo;

/**
 * 저장 출처.
<p>값 목록은 확정 전입니다 (backend_blueprint.md 8.7 #2).
 */
public enum SavedPlaceSource {
    FEED,
    COLLECTION_SNAPSHOT,
    AI_IMPORT,
    DIRECT
}
