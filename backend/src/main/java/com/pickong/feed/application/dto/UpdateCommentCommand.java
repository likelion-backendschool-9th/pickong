package com.pickong.feed.application.dto;

import lombok.Getter;

@Getter
public class UpdateCommentCommand {
    private final String content;

    public UpdateCommentCommand(String content) {
        this.content = content;
    }
}
