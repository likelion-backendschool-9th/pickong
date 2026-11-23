package com.pickong.feed.adapter.out.persistence;

import com.pickong.feed.adapter.out.persistence.entity.CommentLikeJpaEntity;
import com.pickong.feed.adapter.out.persistence.repository.CommentLikeJpaRepository;
import com.pickong.feed.application.port.out.CommentReactionRepository;
import org.springframework.stereotype.Component;

/**
 * CommentReactionRepository의 JPA 구현체.
 * comment_likes 테이블 접근을 담당함.
 */
@Component
public class JpaCommentLikeAdapter implements CommentReactionRepository {

    private final CommentLikeJpaRepository commentLikeJpaRepository;

    public JpaCommentLikeAdapter(CommentLikeJpaRepository commentLikeJpaRepository) {
        this.commentLikeJpaRepository = commentLikeJpaRepository;
    }

    @Override
    public void saveCommentLike(Long commentId, Long userId) {
        commentLikeJpaRepository.save(CommentLikeJpaEntity.create(commentId, userId));
    }

    @Override
    public boolean existsCommentLike(Long commentId, Long userId) {
        return commentLikeJpaRepository.existsByCommentIdAndUserId(commentId, userId);
    }

    @Override
    public boolean deleteCommentLike(Long commentId, Long userId) {
        return commentLikeJpaRepository.deleteByCommentIdAndUserId(commentId, userId) > 0;
    }
}
