package com.pickong.place.application.dto;

/** 사용자가 좌표로 장소를 제보할 때 서비스에 넘기는 값. */
public record SubmitPlaceCommand(
        long submitterUserId,
        String name,
        String address,
        String category,
        String description,
        String imageUrl,
        double latitude,
        double longitude) {
}
