package com.pickong.auth.application.port.in;

import com.pickong.auth.application.dto.SocialLoginResult;
import com.pickong.auth.application.dto.SocialProfile;

/** 소셜 로그인 유스케이스. OAuth2 인증이 끝난 직후 OAuth2 성공 처리(웹 어댑터)가 호출합니다. */
public interface LoginUseCase {

    /**
     * 소셜 계정으로 찜콩 회원을 찾아 다음 행동을 정합니다.
     * <ul>
     *   <li>(제공자, 고유 ID)로 연결된 회원이 있으면 → 상태 확인 → {@code ExistingMember} 또는 {@code Unavailable}</li>
     *   <li>없으면 → 인증된 이메일이 다른 회원과 겹치는지 확인 → {@code EmailAlreadyRegistered} 또는 {@code NewMember}</li>
     * </ul>
     * 이메일이 같다고 기존 계정에 자동으로 연결하지는 않습니다.
     */
    SocialLoginResult loginWithSocial(SocialProfile profile);
}
