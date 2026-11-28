package com.pickong.feed.application.dto;

/**
 * 게시물에 연결된 장소 요약
 *
 * @param publicId places.public_id (프론트 장소 id)
 */
public record FeedViewPlace(
        String publicId,
        String name,
        String address,
        String region,
        double lat,
        double lng,
        String category,
        String sourceType,
        String visibility,
        String description) {
}
