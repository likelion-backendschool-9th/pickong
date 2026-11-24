package com.pickong.feed.application.port.out;

// 피드 좋아요 · 북마크 저장소 포트
public interface FeedReactionRepository {

    // 좋아요 존재 여부 확인
    boolean existsLike(Long feedId, Long userId);

    // 좋아요 저장
    void saveLike(Long feedId, Long userId);

    // 좋아요 삭제
    void deleteLike(Long feedId, Long userId);

    // 북마크 존재 여부 확인
    boolean existsBookmark(Long feedId, Long userId);

    // 북마크 저장
    void saveBookmark(Long feedId, Long userId);

    // 북마크 삭제
    void deleteBookmark(Long feedId, Long userId);
}
