package com.pickong.auth.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/**
 * 인증(auth) 모듈 오류 코드.
 * 재발급 실패는 모두 401이라 프론트는 로그인 화면으로 보내고, code는 원인 기록 · 안내 문구용입니다.
 */
@Getter
public enum AuthErrorCode implements ErrorCode {

    /** Refresh Token 쿠키가 없거나, DB에 없거나, 만료됨 */
    INVALID_REFRESH_TOKEN(401, "로그인이 필요해요."),
    /** 이미 폐기된 Refresh Token이 다시 옴(탈취 의심) → 그 로그인 세션 전체를 폐기함 */
    REFRESH_TOKEN_REUSED(401, "보안을 위해 로그아웃됐어요. 다시 로그인해 주세요."),
    /** 정지 · 탈퇴 등으로 ACTIVE가 아닌 회원 */
    ACCOUNT_UNAVAILABLE(401, "이용할 수 없는 계정이에요."),
    /** 임시 가입 쿠키가 없거나 만료됨(30분) → 소셜 로그인부터 다시 */
    SIGNUP_SESSION_EXPIRED(401, "가입 시간이 지났어요. 처음부터 다시 진행해 주세요."),
    /** 가입 세션은 없고 이미 로그인한 회원 (다른 탭에서 가입을 마친 경우 등) → 프론트는 피드로 */
    ALREADY_REGISTERED(409, "이미 가입을 마쳤어요."),
    /** 이 소셜 계정이 이미 찜콩 계정에 연결됨 (같은 가입을 동시에 두 번 제출한 경우 등) */
    SOCIAL_ACCOUNT_ALREADY_LINKED(409, "이미 가입된 소셜 계정이에요. 다시 로그인해 주세요."),
    /** 소셜 계정의 이메일이 이미 다른 찜콩 계정에 연결됨 (1인 1계정) */
    EMAIL_ALREADY_REGISTERED(409, "이미 가입된 이메일이에요."),
    /** 내 계정에 이 제공자의 계정이 이미 연결됨 (제공자마다 하나만 연결) */
    PROVIDER_ALREADY_LINKED(409, "이 로그인 수단은 이미 연결돼 있어요."),
    /** 연결 해제하려는 제공자가 내 계정에 연결돼 있지 않음 */
    SOCIAL_ACCOUNT_NOT_LINKED(404, "연결되지 않은 로그인 수단이에요."),
    /** 가입할 때 쓴 로그인 수단은 해제할 수 없음 (가입 수단 = 가장 먼저 연결된 소셜 계정) */
    SIGNUP_METHOD_CANNOT_BE_UNLINKED(409, "가입할 때 사용한 로그인 수단은 해제할 수 없어요."),
    /** 재동의가 필요한 필수 약관이 시행됐는데 아직 동의하지 않음. 동의 · 로그아웃 · 탈퇴 외의 요청은 이 오류로 막힘 (프론트는 /terms/consent로 이동) */
    TERMS_RECONSENT_REQUIRED(403, "바뀐 약관에 동의해야 계속 이용할 수 있어요."),
    /** 탈퇴 취소 쿠키가 없거나 만료 (10분) → 프론트는 로그인 화면으로 (다시 로그인하면 새로 발급) */
    RESTORE_SESSION_EXPIRED(401, "시간이 지났어요. 다시 로그인해 주세요.");

    private final int status;
    private final String message;

    AuthErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
