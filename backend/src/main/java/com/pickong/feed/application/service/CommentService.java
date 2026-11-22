package com.pickong.feed.application.service;

import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.dto.CreateCommentCommand;
import com.pickong.feed.application.dto.UpdateCommentCommand;
import com.pickong.feed.application.port.in.CommentUseCase;
import com.pickong.feed.application.port.out.CommentRepository;
import com.pickong.feed.domain.model.Comment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService implements CommentUseCase {

    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage getComments(Long feedId, Long cursor, int limit) {
        List<Comment> comments = commentRepository.findByFeedId(feedId, cursor, limit + 1);
        return toPage(comments, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage getReplies(Long feedId, Long commentId, Long cursor, int limit) {
        List<Comment> replies = commentRepository.findRepliesByParentId(commentId, cursor, limit + 1);
        return toPage(replies, limit);
    }

    @Override
    @Transactional
    public Comment createComment(Long feedId, CreateCommentCommand command) {
        Comment comment = Comment.create(feedId, command.getUserId(), command.getParentId(), command.getContent());
        return commentRepository.save(comment);
    }

    @Override
    @Transactional
    public Comment updateComment(Long commentId, UpdateCommentCommand command) {
        Comment comment = commentRepository.findById(commentId);
        // TODO(JWT): JWT 도입 후 comment.getUserId()와 인증된 userId 일치 여부 검증해야됨
        comment.update(command.getContent());
        return commentRepository.save(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        // TODO(JWT): JWT 도입 후 삭제 전 comment.getUserId()와 인증된 userId 일치 여부 검증해야됨
        commentRepository.deleteById(commentId);
    }

    // limit+1개 조회 결과로 CommentPage 생성 (hasNext, nextCursor 계산)
    private CommentPage toPage(List<Comment> comments, int limit) {
        boolean hasNext = comments.size() > limit;
        List<Comment> result = hasNext ? comments.subList(0, limit) : comments;
        Long nextCursor = hasNext ? result.get(result.size() - 1).getId() : null;
        return new CommentPage(result, nextCursor, hasNext);
    }
}
