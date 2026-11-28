package com.pickong.feed.adapter.in.web.response;

import com.pickong.feed.application.dto.CommentView;
import com.pickong.feed.domain.model.Comment;
import java.time.OffsetDateTime;

/**
 * 댓글 응답. 프론트 {@code entities/post/api/dto.ts}의 CommentDto 모양 + 목록 화면용 값(좋아요 수, 대댓글 수)
 * 내부 순번 작성자 id(userId)는 내보내지 않고 author.id(users.public_id)만 내보냄
 *
 * @param id       댓글 id. 경로가 숫자 id를 받아서 숫자 id의 문자열
 * @param postId   게시물 id (게시물 응답의 id와 같은 값)
 * @param parentId 대댓글이면 부모 댓글 id, 아니면 null
 * @param content  삭제된 댓글이면 null (대댓글이 남아 있어 목록에 보이는 경우)
 * @param deleted  삭제된 댓글 여부. true면 프론트에서 "삭제된 댓글입니다" 표시
 */
public record CommentResponse(
        String id,
        String postId,
        String parentId,
        FeedResponse.Author author,
        String content,
        boolean deleted,
        long likeCount,
        long replyCount,
        boolean reply,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CommentResponse from(CommentView view) {
        Comment comment = view.comment();
        boolean deleted = comment.getDeletedAt() != null;
        return new CommentResponse(
                String.valueOf(comment.getId()),
                String.valueOf(comment.getFeedId()),
                comment.getParentId() == null ? null : String.valueOf(comment.getParentId()),
                FeedResponse.Author.from(view.author()),
                deleted ? null : comment.getContent(),
                deleted,
                comment.getLikeCount(),
                comment.getReplyCount(),
                comment.isReply(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
