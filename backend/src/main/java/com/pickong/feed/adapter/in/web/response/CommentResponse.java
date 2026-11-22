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
    private boolean reply;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static CommentResponse from(Comment comment) {
        CommentResponse response = new CommentResponse();
        response.id = comment.getId();
        response.feedId = comment.getFeedId();
        response.userId = comment.getUserId();
        response.parentId = comment.getParentId();
        response.content = comment.getContent();
        response.likeCount = comment.getLikeCount();
        response.reply = comment.isReply();
        response.createdAt = comment.getCreatedAt();
        response.updatedAt = comment.getUpdatedAt();
        return response;
    }
}
