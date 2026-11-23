package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.dto.CreateCommentCommand;
import com.pickong.feed.application.dto.UpdateCommentCommand;
import com.pickong.feed.application.port.in.CommentUseCase;
import com.pickong.feed.application.port.out.CommentRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Comment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService implements CommentUseCase {

    private final CommentRepository commentRepository;
    private final FeedRepository feedRepository;

    public CommentService(CommentRepository commentRepository, FeedRepository feedRepository) {
        this.commentRepository = commentRepository;
        this.feedRepository = feedRepository;
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
        // 부모 댓글이 해당 피드에 속하는지 확인
        Comment parent = commentRepository.findById(commentId);
        if (!parent.getFeedId().equals(feedId)) {
            throw new BusinessException(ErrorCode.COMMENT_FEED_MISMATCH);
        }
        List<Comment> replies = commentRepository.findRepliesByParentId(commentId, cursor, limit + 1);
        return toPage(replies, limit);
    }

    @Override
    @Transactional
    public Comment createComment(Long feedId, CreateCommentCommand command) {
        // 존재하지 않는 feedId면 FEED_NOT_FOUND 예외 발생
        feedRepository.findById(feedId);

        // 대댓글인 경우 부모 댓글 검증
        if (command.getParentId() != null) {
            Comment parent = commentRepository.findById(command.getParentId());
            // 대댓글에 답글 금지 (최대 2단계)
            if (parent.isReply()) {
                throw new BusinessException(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED);
            }
            // 부모 댓글이 현재 피드에 속하는지 확인 — 다른 피드 댓글을 부모로 지정하는 버그 방지
            if (!parent.getFeedId().equals(feedId)) {
                throw new BusinessException(ErrorCode.COMMENT_FEED_MISMATCH);
            }
            // 삭제된 부모 댓글에는 새 대댓글 작성 불가
            if (parent.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.COMMENT_ALREADY_DELETED);
            }
        }
        Comment comment = Comment.create(feedId, command.getUserId(), command.getParentId(), command.getContent());
        return commentRepository.save(comment);
    }

    @Override
    @Transactional
    public Comment updateComment(Long feedId, Long commentId, UpdateCommentCommand command) {
        Comment comment = commentRepository.findById(commentId);
        // 댓글이 해당 피드에 속하는지 확인 — 잘못된 feedId로 다른 피드 댓글 수정 방지
        if (!comment.getFeedId().equals(feedId)) {
            throw new BusinessException(ErrorCode.COMMENT_FEED_MISMATCH);
        }
        // TODO(JWT): JWT 도입 후 comment.getUserId()와 인증된 userId 일치 여부 검증해야됨
        comment.update(command.getContent());
        return commentRepository.save(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long feedId, Long commentId) {
        Comment comment = commentRepository.findById(commentId);
        // 댓글이 해당 피드에 속하는지 확인 — 잘못된 feedId로 다른 피드 댓글 삭제 방지
        if (!comment.getFeedId().equals(feedId)) {
            throw new BusinessException(ErrorCode.COMMENT_FEED_MISMATCH);
        }
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
