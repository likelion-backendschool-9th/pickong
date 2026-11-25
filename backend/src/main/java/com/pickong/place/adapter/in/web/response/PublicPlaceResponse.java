package com.pickong.place.adapter.in.web.response;

import com.pickong.place.domain.model.Place;

/**
 * 공개 장소 목록 항목. id는 장소 공개 id입니다.
 * 공개 상태이고 삭제되지 않은 장소만 조회하며, visibility · owner · description은 내보내지 않습니다.
 */
public record PublicPlaceResponse(
        String id,
        String name,
        String address,
        double lat,
        double lng,
        String category,
        String sourceType) {

    public static PublicPlaceResponse from(Place place) {
        return new PublicPlaceResponse(
                place.getPublicId().toString(),
                place.getName(),
                place.getAddress(),
                place.getLocation().latitude(),
                place.getLocation().longitude(),
                place.getCategory(),
                place.getSourceType().name());
    }
}
