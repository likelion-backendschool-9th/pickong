package com.pickong.place.domain.policy;

/**
 * 지도 장소를 묶는 격자 크기. 화면에서 정사각형이 되도록 메르카토르 투영(도 단위)으로 계산합니다.
 * 크기를 2의 거듭제곱으로 맞춰, 같은 축척에서 지도를 옮겨도 묶음이 바뀌지 않습니다.
 */
public final class MapGridPolicy {

    /** 긴 변을 나누는 최대 칸 수. 2의 거듭제곱으로 올리므로 실제로는 이 값의 절반 ~ 이 값입니다. */
    static final int MAX_CELLS_PER_SIDE = 10;

    /** 메르카토르 투영은 극에서 무한대가 되므로 지도에서 쓰는 한계로 자릅니다. */
    private static final double MAX_LATITUDE = 85.05;

    private MapGridPolicy() {
    }

    /** 범위에 맞는 격자 한 칸의 크기. 항상 0보다 큽니다. */
    public static double cellSize(double west, double south, double east, double north) {
        double width = east - west;
        double height = mercatorLatitude(north) - mercatorLatitude(south);
        double cell = Math.max(Math.max(width, height) / MAX_CELLS_PER_SIDE, Double.MIN_NORMAL);
        return Math.scalb(1.0, (int) Math.ceil(Math.log(cell) / Math.log(2)));
    }

    private static double mercatorLatitude(double latitude) {
        double clamped = Math.clamp(latitude, -MAX_LATITUDE, MAX_LATITUDE);
        return Math.toDegrees(Math.log(Math.tan(Math.toRadians(45 + clamped / 2))));
    }
}
