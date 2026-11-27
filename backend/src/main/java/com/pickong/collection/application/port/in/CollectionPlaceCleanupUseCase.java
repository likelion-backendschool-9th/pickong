package com.pickong.collection.application.port.in;

import java.util.Collection;

/** 저장 장소가 지워질 때 그 장소를 담은 컬렉션 구성(collection_places)을 정리합니다. */
public interface CollectionPlaceCleanupUseCase {

    /**
     * 저장 장소를 모든 컬렉션에서 빼고, 컬렉션의 장소 수(place_count)를 줄입니다.
     * 담긴 컬렉션이 없으면 아무것도 하지 않습니다.
     *
     * @param savedPlaceIds 지울 저장 장소 id
     */
    void removeSavedPlaces(Collection<Long> savedPlaceIds);
}
