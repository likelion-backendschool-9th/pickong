package com.pickong.feed.application.dto;

import lombok.Getter;

@Getter
public class CreateCommentCommand {
    private final Long userId;
    // parentId가 null이면 최상위 댓글, 있으면 대댓글
    private final Long parentId;
    private final String content;

    public CreateCommentCommand(Long userId, Long parentId, String content) {
        this.userId = userId;
        this.parentId = parentId;
        this.content = content;
    }
}
