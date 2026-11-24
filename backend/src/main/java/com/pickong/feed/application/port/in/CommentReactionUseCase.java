package com.pickong.feed.application.port.in;

public interface CommentReactionUseCase {

    // 댓글 좋아요 추가 — 이미 좋아요 했으면 ALREADY_COMMENT_LIKED 예외 발생
    void likeComment(Long feedId, Long commentId, Long userId);

    // 댓글 좋아요 취소 — 좋아요가 없으면 COMMENT_LIKE_NOT_FOUND 예외 발생
    void unlikeComment(Long feedId, Long commentId, Long userId);
}
