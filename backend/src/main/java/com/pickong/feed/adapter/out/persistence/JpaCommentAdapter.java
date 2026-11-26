package com.pickong.feed.adapter.out.persistence;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.adapter.out.persistence.entity.CommentJpaEntity;
import com.pickong.feed.adapter.out.persistence.repository.CommentJpaRepository;
import com.pickong.feed.application.port.out.CommentRepository;
import com.pickong.feed.domain.model.Comment;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.OffsetDateTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Propagation;

@Repository
public class JpaCommentAdapter implements CommentRepository {
    @PersistenceContext
    private EntityManager entityManager;

    private final CommentJpaRepository commentJpaRepository;

    public JpaCommentAdapter(CommentJpaRepository commentJpaRepository) {
        this.commentJpaRepository = commentJpaRepository;
    }

    @Override
    @Transactional
    public Comment save(Comment comment) {
        return commentJpaRepository.save(CommentJpaEntity.from(comment)).toDomain();
    }

    @Override
    public List<Comment> findByFeedId(Long feedId, Long cursor, int limit, Long viewerId, boolean admin) {
        // Object[0] = CommentJpaEntity, Object[1] = 대댓글 수(Long)
        return commentJpaRepository.findTopLevelByFeedId(feedId, cursor, viewerId, admin, PageRequest.of(0, limit))
                .stream()
                .map(row -> {
                    CommentJpaEntity entity = (CommentJpaEntity) row[0];
                    long replyCount = (Long) row[1];
                    return entity.toDomainWithReplyCount(replyCount);
                })
                .toList();
    }

    @Override
    public List<Comment> findRepliesByParentId(Long parentId, Long cursor, int limit, Long viewerId, boolean admin) {
        return commentJpaRepository.findRepliesByParentId(parentId, cursor, viewerId, admin, PageRequest.of(0, limit))
                .stream().map(CommentJpaEntity::toDomain).toList();
    }

    @Override
    public Comment findById(Long commentId) {
        return commentJpaRepository.findById(commentId)
                .map(CommentJpaEntity::toDomain)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Comment findByIdForUpdate(Long commentId) {
        CommentJpaEntity entity = commentJpaRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND));
        entityManager.refresh(entity);
        return entity.toDomain();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean updateHiddenAt(long commentId, OffsetDateTime previous, OffsetDateTime hiddenAt, OffsetDateTime now) {
        return commentJpaRepository.updateHiddenAt(commentId, previous, hiddenAt, now) == 1;
    }

    @Override
    @Transactional
    public void deleteById(Long commentId) {
        // 물리 삭제 대신 deletedAt 설정 (소프트 삭제) — 대댓글 고아 방지
        CommentJpaEntity entity = commentJpaRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND));
        entity.softDelete();
    }

    @Override
    @Transactional
    public void incrementLikeCount(Long commentId) {
        commentJpaRepository.incrementLikeCount(commentId);
    }

    @Override
    @Transactional
    public void decrementLikeCount(Long commentId) {
        commentJpaRepository.decrementLikeCount(commentId);
    }
}
