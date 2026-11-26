package com.pickong.place.domain.policy;

/**
 * 지도 장소 조회 한 번에 허용하는 범위 크기.
 * 프론트(entities/place/boundsQuery.ts)는 한 변이 15km를 넘으면 요청하지 않고,
 * 보낼 때는 범위를 바깥쪽으로 약 100m씩 넓히므로 서버는 여유를 두고 16km까지 받습니다.
 */
public final class MapBoundsPolicy {

    /** 가로 · 세로 한 변의 최대 길이(m) */
    static final double MAX_SPAN_METERS = 16_000;

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private MapBoundsPolicy() {
    }

    /** 가로(중앙 위도 기준) 또는 세로 길이가 최대 길이를 넘으면 true */
    public static boolean isTooLarge(double west, double south, double east, double north) {
        double midLatitude = (south + north) / 2;
        double height = distanceMeters(south, west, north, west);
        double width = distanceMeters(midLatitude, west, midLatitude, east);
        return width > MAX_SPAN_METERS || height > MAX_SPAN_METERS;
    }

    /** 두 좌표 사이 거리(m). 프론트 shared/lib/geo.ts의 distanceMeters와 같은 하버사인 공식입니다. */
    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }
}
