package com.pickong.feed.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 피드-장소 조인 쿼리 한 행.
 * place_id 기준으로 그룹핑해서 피드 ID 목록을 모음.
 */
@Getter
@AllArgsConstructor
public class FeedPlaceSummary {
    private Long placeId;
    private String placePublicId;  // UUID → String
    private String placeName;
    private String placeAddress;
    private double lat;
    private double lng;
    private String category;
    private String sourceType;
    private Long feedId;
}
