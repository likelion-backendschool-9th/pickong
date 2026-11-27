package com.pickong.savedplace.adapter.in.web.request;

import java.util.List;

/**
 * POST /saved-places. 프론트 SavePlacesInput과 같은 형식입니다.
 *
 * @param placeIds      장소 공개 id
 * @param collectionIds 저장하면서 담을 컬렉션. collections 마이그레이션 전이라 받기만 하고 쓰지 않습니다 (8.1 #13)
 * @param source        저장 경로. 생략하면 직접 저장
 */
public record SaveSavedPlacesRequest(List<String> placeIds, List<String> collectionIds, Source source) {

    /**
     * @param type  POST · COLLECTION · LINK_IMPORT · SEARCH · CUSTOM
     * @param refId POST면 게시물 공개 id, LINK_IMPORT면 원문 URL
     * @param label 화면에 보여 줄 출처 문구
     */
    public record Source(String type, String refId, String label) {
    }
}
