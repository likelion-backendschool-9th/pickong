package com.pickong.user.application.port.in;

import com.pickong.user.application.dto.MyProfile;
import com.pickong.user.application.dto.UpdateProfileCommand;

/** 회원 정보 변경 유스케이스. (blueprint 3장: PATCH /me → UserCommandUseCase.updateProfile) */
public interface UserCommandUseCase {

    /**
     * 내 프로필에서 보낸 항목만 바꾸고 바뀐 내 정보를 돌려줍니다.
     *
     * @throws com.pickong.common.error.BusinessException
     *         400 INVALID_USERNAME · INVALID_NAME · INVALID_BIO · INVALID_REGION,
     *         409 USERNAME_TAKEN(사용 중 · 예약어) · USERNAME_CHANGE_TOO_SOON(14일 이내 재변경)
     */
    MyProfile updateProfile(Long userId, UpdateProfileCommand command);
}
