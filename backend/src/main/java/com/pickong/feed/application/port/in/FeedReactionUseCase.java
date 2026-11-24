package com.pickong.feed.application.port.in;

// 피드 좋아요 · 북마크 추가/취소 유스케이스
public interface FeedReactionUseCase {

    // 좋아요 추가 — 이미 눌렀으면 ALREADY_LIKED 예외
    void likeFeed(Long feedId, Long userId);

    // 좋아요 취소 — 누르지 않은 상태면 LIKE_NOT_FOUND 예외
    void unlikeFeed(Long feedId, Long userId);

    // 북마크 추가 — 이미 했으면 ALREADY_BOOKMARKED 예외
    void bookmarkFeed(Long feedId, Long userId);

    // 북마크 취소 — 하지 않은 상태면 BOOKMARK_NOT_FOUND 예외
    void unbookmarkFeed(Long feedId, Long userId);
}
