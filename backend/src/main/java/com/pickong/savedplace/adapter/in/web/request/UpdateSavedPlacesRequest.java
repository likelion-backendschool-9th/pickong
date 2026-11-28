package com.pickong.savedplace.adapter.in.web.request;

import java.util.List;

/**
 * PATCH /saved-places. 프론트 UpdateSavedPlacesInput과 같은 형식입니다. ids 외에는 보낸 값만 바꿉니다.
 * <ul>
 *   <li>memo: 생략하거나 null이면 그대로, 빈 문자열(공백만 포함)이면 메모를 지웁니다</li>
 *   <li>컬렉션 필드(add · set · removeCollectionIds): 아직 지원하지 않아 값이 있으면 400 COLLECTION_LINK_NOT_SUPPORTED,
 *       빈 목록은 받습니다 (8.1 #13)</li>
 * </ul>
 */
public record UpdateSavedPlacesRequest(
        List<String> ids,
        String visitStatus,
        String memo,
        List<String> addCollectionIds,
        List<String> setCollectionIds,
        List<String> removeCollectionIds) {
}
