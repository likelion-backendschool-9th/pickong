package com.pickong.savedplace.adapter.in.web.request;

import java.util.List;

/**
 * PATCH /saved-places. 프론트 UpdateSavedPlacesInput과 같은 형식입니다. ids 외에는 보낸 값만 바꿉니다.
 * 컬렉션 필드(add · set · removeCollectionIds)는 collections 마이그레이션 전이라 받기만 하고 쓰지 않습니다 (8.1 #13).
 */
public record UpdateSavedPlacesRequest(
        List<String> ids,
        String visitStatus,
        String memo,
        List<String> addCollectionIds,
        List<String> setCollectionIds,
        List<String> removeCollectionIds) {
}
