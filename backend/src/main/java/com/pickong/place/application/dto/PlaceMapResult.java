package com.pickong.place.application.dto;

import java.util.List;

/** 지도 범위 조회 결과. 혼자 있는 장소와 묶음을 나눠 담습니다. */
public record PlaceMapResult(List<PlaceMapItem> places, List<PlaceCluster> clusters) {
}
