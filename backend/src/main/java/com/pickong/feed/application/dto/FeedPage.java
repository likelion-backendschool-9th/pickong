package com.pickong.feed.application.dto;

import com.pickong.feed.domain.model.Feed;
import lombok.Getter;

import java.util.List;

// 피드 목록 페이지네이션 결과를 담는 application 계층 DTO
@Getter
public class FeedPage {

    private final List<Feed> feeds;
    private final Long nextCursor;
    private final boolean hasNext;

    public FeedPage(List<Feed> feeds, Long nextCursor, boolean hasNext) {
        this.feeds = feeds;
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
    }
}
