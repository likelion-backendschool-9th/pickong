package com.pickong.feed.application.port.in;

import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.dto.CreateCommentCommand;
import com.pickong.feed.application.dto.UpdateCommentCommand;
import com.pickong.feed.domain.model.Comment;

public interface CommentUseCase {

    // cursor: 마지막으로 조회한 commentId (첫 요청 시 null)
    CommentPage getComments(Long feedId, Long cursor, int limit);

    CommentPage getReplies(Long feedId, Long commentId, Long cursor, int limit);

    Comment createComment(Long feedId, CreateCommentCommand command);

    Comment updateComment(Long commentId, UpdateCommentCommand command);

    void deleteComment(Long commentId);
}
