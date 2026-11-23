package com.pickong.common.error;

import lombok.Getter;

@Getter
public enum ErrorCode {

    FEED_NOT_FOUND(404, "피드를 찾을 수 없습니다"),
    COMMENT_NOT_FOUND(404, "댓글을 찾을 수 없습니다"),
    COMMENT_REPLY_DEPTH_EXCEEDED(400, "대댓글에는 답글을 달 수 없습니다"),
    COMMENT_FEED_MISMATCH(400, "댓글이 해당 피드에 속하지 않습니다"),
    COMMENT_ALREADY_DELETED(400, "삭제된 댓글에는 답글을 달 수 없습니다");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
