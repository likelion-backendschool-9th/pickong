package com.pickong.user.application.port.in;

/**
 * auth 모듈 전용 회원 계정 기능. (blueprint 4.3: 다른 모듈 전용 in-port는 이름으로 사용처를 드러냄)
 * auth는 users 테이블을 직접 읽지 않고 이 포트로만 회원 정보를 묻습니다.
 */
public interface UserAccountUseCase {

    /** 이 회원이 로그인 · 토큰 재발급을 할 수 있는지. 회원이 없거나 ACTIVE가 아니면 false */
    boolean canLogin(Long userId);
}
