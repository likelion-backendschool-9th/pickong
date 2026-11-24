package com.pickong.user.application.port.in;

import com.pickong.user.application.dto.RegisterUserCommand;
import com.pickong.user.application.dto.RegisteredUser;

/**
 * auth 모듈 전용 회원 계정 기능. (blueprint 4.3: 다른 모듈 전용 in-port는 이름으로 사용처를 드러냄)
 * auth는 users 테이블을 직접 읽거나 쓰지 않고 이 포트로만 회원 정보를 다룹니다.
 */
public interface UserAccountUseCase {

    /** 이 회원이 로그인 · 토큰 재발급을 할 수 있는지. 회원이 없거나 ACTIVE가 아니면 false */
    boolean canLogin(Long userId);

    /**
     * 온보딩을 마친 신규 회원을 만듭니다. 호출한 쪽(가입 완료)의 트랜잭션에 합류합니다.
     *
     * @throws com.pickong.common.error.BusinessException
     *         400 INVALID_USERNAME · INVALID_NAME, 409 USERNAME_TAKEN(사용 중 · 예약어) · EMAIL_ALREADY_REGISTERED
     */
    RegisteredUser register(RegisterUserCommand command);
}
