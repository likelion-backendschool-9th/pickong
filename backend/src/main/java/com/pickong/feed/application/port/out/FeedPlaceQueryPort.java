package com.pickong.feed.application.port.out;

import com.pickong.feed.application.dto.FeedPlaceSummary;
import java.util.List;

/**
 * 피드 지도용 장소 조회 포트.
 * places · follows 등 다른 모듈 테이블을 JOIN하는 조회 전용 포트.
 * 구현: feed.adapter.out.persistence.query.FeedPlaceQueryAdapter
 */
public interface FeedPlaceQueryPort {

    // 추천 탭과 같은 기준: 다른 사람의 PUBLIC 게시물에 연결된 공개 · 삭제되지 않은 장소. viewerId가 null이면 PUBLIC 전체
    List<FeedPlaceSummary> findRecommendedFeedPlaces(Long viewerId);

    // 팔로잉 탭과 같은 기준: 내 게시물 전부 + 팔로우한 사람의 PUBLIC, FOLLOWERS 게시물에 연결된 공개 · 삭제되지 않은 장소
    List<FeedPlaceSummary> findFollowingFeedPlaces(Long userId);
}
