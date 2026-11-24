package com.pickong.auth.application.port.in;

import com.pickong.auth.application.dto.SignUpCommand;
import com.pickong.auth.application.dto.SignUpResult;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.common.security.LoginUser;

/**
 * 최초 가입 온보딩 유스케이스. 소셜 로그인 직후 받은 임시 가입 토큰으로만 쓸 수 있습니다.
 * 세션(Access · Refresh Token) 발급은 하지 않고, 웹 어댑터가 가입 완료 뒤 TokenUseCase로 발급합니다.
 */
public interface SignUpUseCase {

    /**
     * 진행 중인 가입의 소셜 계정 정보. (온보딩 화면의 기본값: 이름 · 사진 · 이메일)
     *
     * @param signupToken 임시 가입 쿠키 값 (없으면 null)
     * @param currentUser 이미 로그인한 회원 (없으면 null)
     * @throws com.pickong.common.error.BusinessException 401 SIGNUP_SESSION_EXPIRED · 409 ALREADY_REGISTERED
     */
    SocialProfile getSession(String signupToken, LoginUser currentUser);

    /**
     * 가입을 마칩니다. 회원 생성 · 소셜 계정 연결 · 약관 동의 기록이 <b>한 트랜잭션</b>이라, 하나라도 실패하면 아무것도 남지 않습니다.
     *
     * @throws com.pickong.common.error.BusinessException 401 SIGNUP_SESSION_EXPIRED, 409 ALREADY_REGISTERED ·
     *         SOCIAL_ACCOUNT_ALREADY_LINKED · EMAIL_ALREADY_REGISTERED · USERNAME_TAKEN,
     *         400 INVALID_USERNAME · INVALID_NAME · REQUIRED_TERMS_NOT_AGREED · UNKNOWN_TERMS
     */
    SignUpResult signUp(String signupToken, LoginUser currentUser, SignUpCommand command);
}
