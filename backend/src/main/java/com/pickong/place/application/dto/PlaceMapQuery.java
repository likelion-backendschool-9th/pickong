package com.pickong.place.application.dto;

/**
 * 지도 범위 조회 조건. 경도 west~east, 위도 south~north.
 *
 * @param category 장소 카테고리. null이면 전체
 */
public record PlaceMapQuery(double west, double south, double east, double north, String category) {

    public PlaceMapQuery {
        category = category == null || category.isBlank() ? null : category.trim();
    }
}
