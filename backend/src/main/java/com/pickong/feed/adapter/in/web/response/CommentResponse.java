package com.pickong.feed.adapter.in.web.response;

import com.pickong.feed.domain.model.Comment;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class CommentResponse {
    private Long id;
    private Long feedId;
    private Long userId;
    private Long parentId;
    private String content;
    private long likeCount;
    // 살아있는 대댓글 수 — 프론트에서 "답글 N개 보기" UI에 사용
    private long replyCount;
    private boolean reply;
    // 삭제된 댓글 여부 — true면 프론트에서 "삭제된 댓글입니다" 표시
    private boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static CommentResponse from(Comment comment) {
        CommentResponse response = new CommentResponse();
        response.id = comment.getId();
        response.feedId = comment.getFeedId();
        response.userId = comment.getUserId();
        response.parentId = comment.getParentId();
        response.deleted = comment.getDeletedAt() != null;
        // 삭제된 댓글은 content를 null로 — 프론트에서 "삭제된 댓글입니다" 표시
        response.content = response.deleted ? null : comment.getContent();
        response.likeCount = comment.getLikeCount();
        response.replyCount = comment.getReplyCount();
        response.reply = comment.isReply();
        response.createdAt = comment.getCreatedAt();
        response.updatedAt = comment.getUpdatedAt();
        return response;
    }
}
