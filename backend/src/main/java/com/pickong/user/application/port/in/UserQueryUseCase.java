package com.pickong.user.application.port.in;

import com.pickong.user.application.dto.MyProfile;

/** 회원 조회 유스케이스. (blueprint 3장: GET /me → getMe, GET /auth/username-availability → isUsernameAvailable) */
public interface UserQueryUseCase {

    /** 내 정보. 로그인한 회원이므로 반드시 있습니다(없으면 IllegalStateException — 토큰은 있는데 회원 행이 없는 비정상 상태). */
    MyProfile getMe(Long userId);

    /**
     * 이 아이디를 새로 쓸 수 있는지. 대소문자를 무시하고 비교하며, 예약어는 '사용 중'으로 봅니다.
     *
     * @throws com.pickong.common.error.BusinessException 400 INVALID_USERNAME — 형식이 맞지 않을 때
     */
    boolean isUsernameAvailable(String username);

    /**
     * username으로 사용자 id를 조회함. 대소문자 무시.
     * 다른 모듈이 username → userId 변환에 사용하는 메서드.
     *
     * @throws com.pickong.common.error.BusinessException 404 USER_NOT_FOUND — 없는 username일 때
     */
    Long findIdByUsername(String username);
}
