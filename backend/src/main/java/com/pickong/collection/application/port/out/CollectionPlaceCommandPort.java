package com.pickong.collection.application.port.out;

import java.util.Collection;

/** 컬렉션 구성 장소(collection_places) 쓰기. */
public interface CollectionPlaceCommandPort {

    /** 저장 장소를 담은 구성 행을 지우고, 지운 만큼 각 컬렉션의 place_count를 줄입니다. */
    void deleteBySavedPlaceIds(Collection<Long> savedPlaceIds);
}
