package com.pickong.feed.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 게시물에 태그된 장소. Feed 애그리거트 안에서만 만들고 바꿉니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FeedPlace {
    private Long id;
    private Long placeId;

    static FeedPlace create(Long placeId) {
        return FeedPlace.builder().placeId(placeId).build();
    }
}
