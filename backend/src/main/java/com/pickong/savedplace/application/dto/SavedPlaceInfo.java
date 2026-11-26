package com.pickong.savedplace.application.dto;

import java.util.UUID;

/** 저장한 장소의 표시 정보. 내부 id 대신 공개 id만 담습니다. */
public record SavedPlaceInfo(
        UUID publicId,
        String name,
        String address,
        String region,
        double latitude,
        double longitude,
        String category,
        String sourceType,
        String visibility,
        String description,
        String imageUrl) {
}
