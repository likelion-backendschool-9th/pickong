package com.pickong.place.application.dto;

import java.util.UUID;

/** 지도에 표시하는 장소 한 곳. */
public record PlaceMapItem(
        UUID publicId,
        String name,
        String address,
        double latitude,
        double longitude,
        String category,
        String sourceType) {
}
