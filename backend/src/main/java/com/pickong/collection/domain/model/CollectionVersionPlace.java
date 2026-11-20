package com.pickong.collection.domain.model;

import com.pickong.common.domain.GeoPoint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 버전 스냅샷에 담긴 장소. CollectionVersion 애그리거트 안에서만 다룹니다.
 * <p>detailSnapshot은 영업시간 · 전화 · 웹 등 비정형 스냅샷(JSON)입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionVersionPlace {
    private Long id;
    private Long placeId;
    private int sortOrder;
    private String name;
    private String address;
    private GeoPoint location;
    private String category;
    private String detailSnapshot;
}
