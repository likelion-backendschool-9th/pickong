package com.pickong.savedplace.adapter.in.web.request;

import java.util.List;

/**
 * POST /saved-places. 프론트 SavePlacesInput과 같은 형식입니다.
 *
 * @param placeIds      장소 공개 id
 * @param collectionIds 저장하면서 담을 컬렉션. 아직 지원하지 않아 값이 있으면 400 COLLECTION_LINK_NOT_SUPPORTED, 빈 목록은 받음 (8.1 #13)
 * @param source        저장 경로. 생략하면 직접 저장
 */
public record SaveSavedPlacesRequest(List<String> placeIds, List<String> collectionIds, Source source) {

    /**
     * @param type  POST · COLLECTION · LINK_IMPORT · SEARCH · CUSTOM
     * @param refId POST면 게시물 공개 id, COLLECTION이면 컬렉션 공개 id, LINK_IMPORT면 원문 URL
     * @param label 화면에 보여 줄 출처 문구
     */
    public record Source(String type, String refId, String label) {
    }
}
