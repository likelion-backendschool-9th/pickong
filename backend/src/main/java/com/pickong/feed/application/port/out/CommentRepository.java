package com.pickong.feed.application.port.out;

import com.pickong.feed.domain.model.Comment;

import java.util.List;
import java.time.OffsetDateTime;

public interface CommentRepository {
    Comment save(Comment comment);

    // feedId에 속한 최상위 댓글 목록 (cursor 이후 limit+1개)
    List<Comment> findByFeedId(Long feedId, Long cursor, int limit, Long viewerId, boolean admin);

    // commentId 아래 대댓글 목록 (cursor 이후 limit+1개)
    List<Comment> findRepliesByParentId(Long parentId, Long cursor, int limit, Long viewerId, boolean admin);

    Comment findById(Long commentId);

    Comment findByIdForUpdate(Long commentId);

    boolean updateHiddenAt(long commentId, OffsetDateTime previous, OffsetDateTime hiddenAt, OffsetDateTime now);

    void deleteById(Long commentId);

    void incrementLikeCount(Long commentId);

    void decrementLikeCount(Long commentId);
}
