package com.pickong.place.adapter.in.web.response;

import com.pickong.place.application.dto.PlaceMapItem;
import com.pickong.place.application.dto.PlaceMapResult;
import java.util.List;

/** 공개 장소 조회 응답. 혼자 있는 장소는 places, 묶은 장소는 clusters에 들어갑니다. */
public record PublicPlaceMapResponse(List<PublicPlaceResponse> places, List<PlaceClusterResponse> clusters) {

    public static PublicPlaceMapResponse from(PlaceMapResult result) {
        return new PublicPlaceMapResponse(
                result.places().stream().map(PublicPlaceResponse::from).toList(),
                result.clusters().stream().map(PlaceClusterResponse::from).toList());
    }

    public static PublicPlaceMapResponse ofItems(List<PlaceMapItem> places) {
        return of(places.stream().map(PublicPlaceResponse::from).toList());
    }

    /** 묶지 않은 목록. clusters는 비어 있습니다. */
    public static PublicPlaceMapResponse of(List<PublicPlaceResponse> places) {
        return new PublicPlaceMapResponse(places, List.of());
    }
}
