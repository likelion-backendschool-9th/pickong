package com.pickong.feed.application.port.out;

import com.pickong.feed.domain.model.Comment;

import java.util.List;

public interface CommentRepository {
    Comment save(Comment comment);

    // feedId에 속한 최상위 댓글 목록 (cursor 이후 limit+1개)
    List<Comment> findByFeedId(Long feedId, Long cursor, int limit);

    // commentId 아래 대댓글 목록 (cursor 이후 limit+1개)
    List<Comment> findRepliesByParentId(Long parentId, Long cursor, int limit);

    Comment findById(Long commentId);

    void deleteById(Long commentId);

    void incrementLikeCount(Long commentId);

    void decrementLikeCount(Long commentId);
}
