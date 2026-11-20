package com.pickong.common.domain;

/**
 * 위도 · 경도 좌표 (WGS84).
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("위도는 -90 ~ 90 사이여야 합니다: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("경도는 -180 ~ 180 사이여야 합니다: " + longitude);
        }
    }
}
