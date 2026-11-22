package com.pickong.feed.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 게시물(feed) 모듈 오류 코드. */
@Getter
public enum FeedErrorCode implements ErrorCode {

    FEED_NOT_FOUND(404, "피드를 찾을 수 없습니다");

    private final int status;
    private final String message;

    FeedErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
