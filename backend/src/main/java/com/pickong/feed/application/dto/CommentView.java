package com.pickong.feed.application.dto;

import com.pickong.feed.domain.model.Comment;

/** 화면에 보여 줄 댓글 한 건: 댓글 + 작성자 요약 */
public record CommentView(Comment comment, FeedAuthor author) {
}
