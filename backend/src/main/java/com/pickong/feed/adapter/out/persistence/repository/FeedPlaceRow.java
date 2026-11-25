package com.pickong.feed.adapter.out.persistence.repository;

/**
 * 피드-장소 조인 쿼리 결과를 받는 Spring Data Projection 인터페이스.
 * 메서드 이름이 SELECT 절의 AS 별칭과 정확히 일치해야 함.
 */
public interface FeedPlaceRow {
    Long getPlaceId();
    String getPlacePublicId();
    String getPlaceName();
    String getPlaceAddress();
    Double getLat();
    Double getLng();
    String getCategory();
    String getSourceType();
    Long getFeedId();
}
