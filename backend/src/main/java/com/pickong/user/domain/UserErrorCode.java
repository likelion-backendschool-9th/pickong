package com.pickong.user.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 회원(user) 모듈 오류 코드. */
@Getter
public enum UserErrorCode implements ErrorCode {

    /** 닉네임(= 사용자 ID) 형식이 맞지 않음 */
    INVALID_USERNAME(400, "닉네임은 영문으로 시작하는 3~20자로, 영문 소문자 · 숫자 · 밑줄(_) · 마침표(.)만 쓸 수 있어요."),
    /** 이미 다른 회원이 쓰거나 예약어인 닉네임 */
    USERNAME_TAKEN(409, "이미 사용 중인 닉네임이에요."),
    /** 이름이 너무 김 */
    INVALID_NAME(400, "이름은 20자까지 쓸 수 있어요."),
    /** 소개가 너무 김 */
    INVALID_BIO(400, "소개는 150자까지 쓸 수 있어요."),
    /** 활동 지역이 너무 김 */
    INVALID_REGION(400, "활동 지역은 20자까지 쓸 수 있어요."),
    /** 마지막 아이디 변경 후 14일이 지나지 않음. 다음 변경 가능 시각은 GET /me의 usernameChangeableAt */
    USERNAME_CHANGE_TOO_SOON(409, "아이디는 14일에 한 번만 바꿀 수 있어요."),
    /** 가입 이메일이 이미 다른 회원에게 있음 (1인 1계정) */
    EMAIL_ALREADY_REGISTERED(409, "이미 가입된 이메일이에요."),
    /** 사용자를 찾을 수 없음 */
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다");

    private final int status;
    private final String message;

    UserErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
