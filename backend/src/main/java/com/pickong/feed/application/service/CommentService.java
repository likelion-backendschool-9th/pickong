package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.dto.CreateCommentCommand;
import com.pickong.feed.application.dto.UpdateCommentCommand;
import com.pickong.feed.application.port.in.CommentUseCase;
import com.pickong.feed.application.port.out.CommentRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Comment;
import com.pickong.feed.domain.model.Feed;
import com.pickong.social.application.port.in.RelationQueryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService implements CommentUseCase {

    private final CommentRepository commentRepository;
    private final FeedRepository feedRepository;
    private final RelationQueryUseCase relationQueryUseCase;

    public CommentService(CommentRepository commentRepository, FeedRepository feedRepository,
                          RelationQueryUseCase relationQueryUseCase) {
        this.commentRepository = commentRepository;
        this.feedRepository = feedRepository;
        this.relationQueryUseCase = relationQueryUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage getComments(Long feedId, Long cursor, int limit) {
        return getComments(feedId, cursor, limit, null, false);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage getComments(Long feedId, Long cursor, int limit, Long viewerId, boolean admin) {
        ensureFeedReadable(feedRepository.findById(feedId), viewerId, admin);
        List<Comment> comments = commentRepository.findByFeedId(feedId, cursor, limit + 1, viewerId, admin);
        return toPage(comments, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage getReplies(Long feedId, Long commentId, Long cursor, int limit) {
        return getReplies(feedId, commentId, cursor, limit, null, false);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage getReplies(Long feedId, Long commentId, Long cursor, int limit, Long viewerId, boolean admin) {
        ensureFeedReadable(feedRepository.findById(feedId), viewerId, admin);
        // 부모 댓글이 해당 피드에 속하는지 확인
        Comment parent = commentRepository.findById(commentId);
        if (!parent.getFeedId().equals(feedId)) {
            throw new BusinessException(FeedErrorCode.COMMENT_FEED_MISMATCH);
        }
        parent.ensureReadable(viewerId, admin);
        List<Comment> replies = commentRepository.findRepliesByParentId(commentId, cursor, limit + 1, viewerId, admin);
        return toPage(replies, limit);
    }

    @Override
    @Transactional
    public Comment createComment(Long feedId, CreateCommentCommand command) {
        // 볼 수 없거나 숨김인 게시물이면 FEED_NOT_FOUND (공개 범위 포함)
        Feed feed = feedRepository.findByIdForUpdate(feedId);
        feed.ensureAcceptsInteraction(relationQueryUseCase.relationOf(command.getUserId(), feed.getUserId()));

        // 대댓글인 경우 부모 댓글 검증
        if (command.getParentId() != null) {
            Comment parent = commentRepository.findById(command.getParentId());
            // 대댓글에 답글 금지 (최대 2단계)
            if (parent.isReply()) {
                throw new BusinessException(FeedErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED);
            }
            // 부모 댓글이 현재 피드에 속하는지 확인 — 다른 피드 댓글을 부모로 지정하는 버그 방지
            if (!parent.getFeedId().equals(feedId)) {
                throw new BusinessException(FeedErrorCode.COMMENT_FEED_MISMATCH);
            }
            // 삭제된 부모 댓글에는 새 대댓글 작성 불가
            if (parent.getDeletedAt() != null) {
                throw new BusinessException(FeedErrorCode.COMMENT_ALREADY_DELETED);
            }
            if (parent.getHiddenAt() != null) throw new BusinessException(FeedErrorCode.COMMENT_HIDDEN);
        }
        Comment comment = Comment.create(feedId, command.getUserId(), command.getParentId(), command.getContent());
        return commentRepository.save(comment);
    }

    @Override
    @Transactional
    public Comment updateComment(Long feedId, Long commentId, Long userId, UpdateCommentCommand command) {
        // 1. 게시물을 볼 수 있는지 → 2. 댓글이 이 게시물 것인지 → 3. 삭제된 댓글인지 → 4. 본인 댓글인지
        // 1번을 먼저 해야 볼 수 없는 글에서 COMMENT_NOT_OWNER 같은 다른 오류로 글의 존재가 드러나지 않음
        // 3번도 4번보다 먼저 해야 남의 삭제된 댓글에 403이 아닌 404가 나감
        ensureFeedReadable(feedRepository.findByIdForUpdate(feedId), userId, false);
        Comment comment = commentRepository.findByIdForUpdate(commentId);
        // 댓글이 해당 피드에 속하는지 확인 — 잘못된 feedId로 다른 피드 댓글 수정 방지
        if (!comment.getFeedId().equals(feedId)) {
            throw new BusinessException(FeedErrorCode.COMMENT_FEED_MISMATCH);
        }
        comment.ensureNotDeleted();
        // 본인 댓글만 수정 가능
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(FeedErrorCode.COMMENT_NOT_OWNER);
        }
        // 관리자가 숨긴 댓글도 작성자는 수정할 수 있고 숨김 상태는 그대로 유지됨 (신고 처리 정책, 차단 여부는 #183에서 논의)
        comment.update(command.getContent());
        return commentRepository.save(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long feedId, Long commentId, Long userId) {
        // 수정과 같은 순서: 게시물 열람 가능 → 댓글 소속 → 삭제 여부 → 본인 댓글 (숨김 댓글은 내 글이라 삭제 허용)
        ensureFeedReadable(feedRepository.findByIdForUpdate(feedId), userId, false);
        Comment comment = commentRepository.findByIdForUpdate(commentId);
        // 댓글이 해당 피드에 속하는지 확인 — 잘못된 feedId로 다른 피드 댓글 삭제 방지
        if (!comment.getFeedId().equals(feedId)) {
            throw new BusinessException(FeedErrorCode.COMMENT_FEED_MISMATCH);
        }
        // 이미 삭제된 댓글을 다시 지우면 404 (삭제 시각이 덮어써지지 않게)
        comment.ensureNotDeleted();
        // 본인 댓글만 삭제 가능
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(FeedErrorCode.COMMENT_NOT_OWNER);
        }
        commentRepository.deleteById(commentId);
    }

    // 관리자면 관계를 계산하지 않고 통과, 아니면 보는 사람과 작성자의 관계로 공개 범위 판정
    private void ensureFeedReadable(Feed feed, Long viewerId, boolean admin) {
        feed.ensureReadable(admin ? null : relationQueryUseCase.relationOf(viewerId, feed.getUserId()), admin);
    }

    // limit+1개 조회 결과로 CommentPage 생성 (hasNext, nextCursor 계산)
    private CommentPage toPage(List<Comment> comments, int limit) {
        boolean hasNext = comments.size() > limit;
        List<Comment> result = hasNext ? comments.subList(0, limit) : comments;
        Long nextCursor = hasNext ? result.get(result.size() - 1).getId() : null;
        return new CommentPage(result, nextCursor, hasNext);
    }
}
