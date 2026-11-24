package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.port.in.CommentReactionUseCase;
import com.pickong.feed.application.port.out.CommentReactionRepository;
import com.pickong.feed.application.port.out.CommentRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Comment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentReactionService implements CommentReactionUseCase {

    private final FeedRepository feedRepository;
    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;

    public CommentReactionService(FeedRepository feedRepository,
                                   CommentRepository commentRepository,
                                   CommentReactionRepository commentReactionRepository) {
        this.feedRepository = feedRepository;
        this.commentRepository = commentRepository;
        this.commentReactionRepository = commentReactionRepository;
    }

    @Override
    @Transactional
    public void likeComment(Long feedId, Long commentId, Long userId) {
        // 피드 존재 확인 — 없으면 FEED_NOT_FOUND 예외
        feedRepository.findById(feedId);

        // 댓글 존재 확인 — 없으면 COMMENT_NOT_FOUND 예외
        Comment comment = commentRepository.findById(commentId);

        // 댓글이 해당 피드에 속하는지 확인 — 잘못된 feedId 방지
        if (!comment.getFeedId().equals(feedId)) {
            throw new BusinessException(FeedErrorCode.COMMENT_FEED_MISMATCH);
        }

        // 삭제된 댓글에는 좋아요 불가
        if (comment.getDeletedAt() != null) {
            throw new BusinessException(FeedErrorCode.COMMENT_ALREADY_DELETED);
        }

        // 숨김 처리된 댓글에는 좋아요 불가
        if (comment.getHiddenAt() != null) {
            throw new BusinessException(FeedErrorCode.COMMENT_HIDDEN);
        }

        // 이미 좋아요했는지 확인 — 중복 좋아요 방지
        if (commentReactionRepository.existsCommentLike(commentId, userId)) {
            throw new BusinessException(FeedErrorCode.ALREADY_COMMENT_LIKED);
        }

        commentReactionRepository.saveCommentLike(commentId, userId);
        commentRepository.incrementLikeCount(commentId);
    }

    @Override
    @Transactional
    public void unlikeComment(Long feedId, Long commentId, Long userId) {
        // 피드 존재 확인
        feedRepository.findById(feedId);

        // 댓글 존재 확인
        Comment comment = commentRepository.findById(commentId);

        // 댓글이 해당 피드에 속하는지 확인
        if (!comment.getFeedId().equals(feedId)) {
            throw new BusinessException(FeedErrorCode.COMMENT_FEED_MISMATCH);
        }

        // 좋아요 삭제 — 없으면 COMMENT_LIKE_NOT_FOUND 예외
        boolean deleted = commentReactionRepository.deleteCommentLike(commentId, userId);
        if (!deleted) {
            throw new BusinessException(FeedErrorCode.COMMENT_LIKE_NOT_FOUND);
        }
        commentRepository.decrementLikeCount(commentId);
    }
}
