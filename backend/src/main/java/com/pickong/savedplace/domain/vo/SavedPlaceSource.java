package com.pickong.savedplace.domain.vo;

/**
 * 저장 출처 종류. ERD 메모(saved_places.source_type)의 값과 같습니다.
 * 프론트는 FEED를 POST라고 부릅니다. 바꾸는 일은 웹 어댑터가 합니다.
 */
public enum SavedPlaceSource {
    /** 피드(게시물)에서 찜. source_feed_id가 있을 수 있습니다. */
    FEED,
    /** 다른 사람 컬렉션에서 찜. source_collection_id는 collections 마이그레이션과 함께 추가합니다. */
    COLLECTION,
    /** AI 링크 가져오기. source_url에 원문 주소가 있을 수 있습니다. */
    LINK_IMPORT,
    /** 검색 · 지도에서 직접 저장 */
    SEARCH,
    /** 직접 등록한 장소 */
    CUSTOM,
    /** 일정 방문 기록으로 저장. API로는 받지 않고 서버가 씁니다. */
    PLAN_VISIT
}
