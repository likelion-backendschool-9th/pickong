package com.pickong.feed.application.port.out;

public interface CommentReactionRepository {

    // 댓글 좋아요 저장
    void saveCommentLike(Long commentId, Long userId);

    // 이미 좋아요했는지 확인
    boolean existsCommentLike(Long commentId, Long userId);

    // 댓글 좋아요 삭제 — 실제로 삭제된 경우 true, 없으면 false 반환
    boolean deleteCommentLike(Long commentId, Long userId);
}
