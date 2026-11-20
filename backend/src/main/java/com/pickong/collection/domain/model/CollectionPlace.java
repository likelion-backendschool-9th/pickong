package com.pickong.collection.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 컬렉션 구성 장소. Collection 애그리거트 안에서만 다룹니다. 순서 앞 2곳이 미리보기입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionPlace {
    private Long id;
    private Long savedPlaceId;
    private Long placeId;
    private int sortOrder;
}
