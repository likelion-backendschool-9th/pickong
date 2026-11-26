package com.pickong.place.application.dto;

/**
 * 지도 조회 한 칸. 장소가 하나면 single이 그 장소이고, 둘 이상이면 single은 null입니다.
 */
public record PlaceMapGroup(PlaceCluster cluster, PlaceMapItem single) {
}
