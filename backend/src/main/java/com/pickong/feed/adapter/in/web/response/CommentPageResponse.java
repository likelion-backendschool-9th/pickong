package com.pickong.feed.adapter.in.web.response;

import lombok.Getter;

import java.util.List;

@Getter
public class CommentPageResponse {
    private List<CommentResponse> comments;
    private Long nextCursor;
    private boolean hasNext;

    public static CommentPageResponse of(List<CommentResponse> comments, Long nextCursor, boolean hasNext) {
        CommentPageResponse response = new CommentPageResponse();
        response.comments = comments;
        response.nextCursor = nextCursor;
        response.hasNext = hasNext;
        return response;
    }
}
