package com.pickong.feed.application.dto;

import com.pickong.feed.domain.model.Comment;
import lombok.Getter;

import java.util.List;

@Getter
public class CommentPage {
    private final List<Comment> comments;
    private final Long nextCursor;
    private final boolean hasNext;

    public CommentPage(List<Comment> comments, Long nextCursor, boolean hasNext) {
        this.comments = comments;
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
    }
}
