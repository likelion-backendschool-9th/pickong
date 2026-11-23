package com.pickong.user.application.port.in;

/** 회원 조회 유스케이스. (blueprint 3장: GET /auth/check-username → UserQueryUseCase.isUsernameAvailable) */
public interface UserQueryUseCase {

    /**
     * 이 닉네임을 새로 쓸 수 있는지. 대소문자를 무시하고 비교하며, 예약어는 '사용 중'으로 봅니다.
     *
     * @throws com.pickong.common.error.BusinessException 400 INVALID_USERNAME — 형식이 맞지 않을 때
     */
    boolean isUsernameAvailable(String username);
}
