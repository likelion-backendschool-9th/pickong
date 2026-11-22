package com.pickong.feed.adapter.in.web.response;

import lombok.Getter;

import java.util.List;

// 피드 목록 페이지네이션 응답 DTO
@Getter
public class FeedPageResponse {

    // 피드 목록
    private List<FeedResponse> feeds;
    // 다음 페이지 시작 커서 (마지막 피드 ID)
    private Long nextCursor;
    // 다음 페이지 존재 여부
    private boolean hasNext;

    public static FeedPageResponse of(List<FeedResponse> feeds, Long nextCursor, boolean hasNext) {
        FeedPageResponse response = new FeedPageResponse();
        response.feeds = feeds;
        response.nextCursor = nextCursor;
        response.hasNext = hasNext;
        return response;
    }
}
