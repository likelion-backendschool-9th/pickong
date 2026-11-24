package com.pickong.place.adapter.in.web.request;

/** 좌표 제보 본문. 제보자 id는 받지 않습니다. */
public record SubmitPlaceRequest(
        String name,
        String address,
        String category,
        String description,
        String imageUrl,
        Double lat,
        Double lng) {
}
