package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.CommentLikeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentLikeJpaRepository extends JpaRepository<CommentLikeJpaEntity, Long> {

    // 해당 댓글에 해당 유저가 좋아요했는지 확인
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    // 좋아요 삭제 — 삭제된 행 수 반환 (0이면 좋아요 없었던 것)
    int deleteByCommentIdAndUserId(Long commentId, Long userId);
}
