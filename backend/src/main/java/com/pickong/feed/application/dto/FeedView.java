package com.pickong.feed.application.dto;

import com.pickong.feed.domain.model.Feed;
import java.util.List;

/**
 * 화면에 보여 줄 게시물 한 건: 게시물 + 다른 모듈 정보(작성자, 사진 주소, 장소) + 보는 사람 기준 좋아요 · 북마크 여부
 *
 * @param photoUrls 업로드가 끝난 사진의 공개 주소 (sort_order 순서)
 */
public record FeedView(
        Feed feed,
        FeedAuthor author,
        List<String> photoUrls,
        List<FeedViewPlace> places,
        boolean liked,
        boolean bookmarked) {
}
