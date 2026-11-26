package com.pickong.feed.application.port.out;

import com.pickong.feed.application.dto.FeedPlaceSummary;
import java.util.List;

/**
 * 피드 지도용 장소 조회 포트.
 * places · follows 등 다른 모듈 테이블을 JOIN하는 조회 전용 포트.
 * 구현: feed.adapter.out.persistence.query.FeedPlaceQueryAdapter
 */
public interface FeedPlaceQueryPort {

    // 추천 탭: PUBLIC 피드에 연결된 공개(PUBLIC) · 삭제되지 않은 장소 목록
    List<FeedPlaceSummary> findRecommendedFeedPlaces();

    // 팔로잉 탭: 팔로잉한 사용자의 PUBLIC 피드에 연결된 공개 · 삭제되지 않은 장소 목록
    List<FeedPlaceSummary> findFollowingFeedPlaces(Long userId);
}
