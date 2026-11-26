package com.pickong.place.application.dto;

/**
 * 지도에서 하나로 묶은 장소들.
 *
 * @param latitude  묶인 장소들의 평균 위도
 * @param longitude 묶인 장소들의 평균 경도
 * @param count     묶인 장소 수. 2 이상
 * @param west      묶인 장소들을 모두 포함하는 범위. 같은 좌표만 묶였으면 west == east, south == north
 */
public record PlaceCluster(
        double latitude,
        double longitude,
        long count,
        double west,
        double south,
        double east,
        double north) {
}
