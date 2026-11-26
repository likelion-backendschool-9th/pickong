package com.pickong.feed.adapter.in.web.response;

import com.pickong.feed.application.dto.FeedPlaceGroup;

import java.util.List;

/**
 * 피드 지도 장소 응답.
 * 장소 정보 + 그 장소에 연결된 피드 ID 목록.
 */
public record FeedPlaceResponse(
        PlaceInfo place,
        List<Long> feedIds,
        int feedCount
) {
    public record PlaceInfo(
            String id,
            String name,
            String address,
            double lat,
            double lng,
            String category,
            String sourceType
    ) {}

    public static FeedPlaceResponse from(FeedPlaceGroup group) {
        PlaceInfo place = new PlaceInfo(
                group.getPlacePublicId(),
                group.getPlaceName(),
                group.getPlaceAddress(),
                group.getLat(),
                group.getLng(),
                group.getCategory(),
                group.getSourceType()
        );
        return new FeedPlaceResponse(place, group.getFeedIds(), group.getFeedIds().size());
    }
}
