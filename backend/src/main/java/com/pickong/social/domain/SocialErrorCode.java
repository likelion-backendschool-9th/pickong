package com.pickong.social.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 소셜(팔로우·차단) 모듈 오류 코드. */
@Getter
public enum SocialErrorCode implements ErrorCode {

    CANNOT_FOLLOW_SELF(400, "자기 자신은 팔로우할 수 없습니다"),
    ALREADY_FOLLOWING(409, "이미 팔로우한 사용자입니다"),
    FOLLOW_NOT_FOUND(404, "팔로우 관계를 찾을 수 없습니다");

    private final int status;
    private final String message;

    SocialErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
