package com.pickong.place.adapter.in.web.response;

import com.pickong.place.application.dto.PlaceCluster;

/**
 * 지도 묶음. bounds는 묶인 장소들을 모두 포함하는 범위이고, 좌표가 모두 같으면 한 점입니다.
 */
public record PlaceClusterResponse(double lat, double lng, long count, Bounds bounds) {

    public record Bounds(double west, double south, double east, double north) {
    }

    public static PlaceClusterResponse from(PlaceCluster cluster) {
        return new PlaceClusterResponse(
                cluster.latitude(),
                cluster.longitude(),
                cluster.count(),
                new Bounds(cluster.west(), cluster.south(), cluster.east(), cluster.north()));
    }
}
