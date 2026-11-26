package com.pickong.feed.application.dto;

import lombok.Getter;

import java.util.List;

/**
 * 피드 지도용 장소 그룹.
 * 같은 장소에 연결된 피드 ID들을 모아서 반환함.
 */
@Getter
public class FeedPlaceGroup {
    private final String placePublicId;
    private final String placeName;
    private final String placeAddress;
    private final double lat;
    private final double lng;
    private final String category;
    private final String sourceType;
    private final List<Long> feedIds;

    public FeedPlaceGroup(FeedPlaceSummary first, List<Long> feedIds) {
        this.placePublicId = first.getPlacePublicId();
        this.placeName = first.getPlaceName();
        this.placeAddress = first.getPlaceAddress();
        this.lat = first.getLat();
        this.lng = first.getLng();
        this.category = first.getCategory();
        this.sourceType = first.getSourceType();
        this.feedIds = feedIds;
    }
}
