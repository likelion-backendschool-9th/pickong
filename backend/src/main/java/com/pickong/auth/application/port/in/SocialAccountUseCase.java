package com.pickong.auth.application.port.in;

import com.pickong.auth.application.dto.LoginMethod;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.domain.vo.SocialProvider;
import java.util.List;
import java.util.Optional;

/**
 * 로그인 수단 관리: 로그인한 회원이 카카오 · Google 계정을 추가로 연결 · 해제합니다.
 * <p>
 * 연결 흐름: {@link #startLink}(연결 토큰 발급) → 브라우저가 OAuth 시작 주소로 이동 → {@link #verifyLinkToken}(시작 시 확인)
 * → 제공자 인증 → 콜백에서 {@link #link}.
 */
public interface SocialAccountUseCase {

    /** 내 로그인 수단 목록(연결 순서대로). 가입할 때 쓴 수단은 해제할 수 없다고 표시합니다. */
    List<LoginMethod> list(Long userId);

    /**
     * 연결을 시작합니다. 이 회원 · 이 제공자 전용의 5분짜리 연결 토큰을 돌려줍니다.
     *
     * @throws com.pickong.common.error.BusinessException PROVIDER_ALREADY_LINKED — 이 제공자가 이미 연결돼 있음
     */
    String startLink(Long userId, SocialProvider provider);

    /** 연결 토큰이 유효하고(서명 · 만료 · 종류) 이 제공자용이면 토큰을 받은 회원 id를, 아니면 빈 값을 돌려줍니다. */
    Optional<Long> verifyLinkToken(String linkToken, SocialProvider provider);

    /**
     * 제공자 인증을 마친 소셜 계정을 회원에 연결합니다. 이미 내 계정에 연결된 같은 계정이면 아무것도 하지 않습니다.
     *
     * @throws com.pickong.common.error.BusinessException ACCOUNT_UNAVAILABLE · SOCIAL_ACCOUNT_ALREADY_LINKED ·
     *                                                    PROVIDER_ALREADY_LINKED · EMAIL_ALREADY_REGISTERED
     */
    void link(Long userId, SocialProfile profile);

    /**
     * 연결을 해제합니다. 찜콩에서만 지우고 카카오 · Google 쪽 앱 연결은 그대로 둡니다.
     *
     * @throws com.pickong.common.error.BusinessException SOCIAL_ACCOUNT_NOT_LINKED · SIGNUP_METHOD_CANNOT_BE_UNLINKED
     */
    void unlink(Long userId, SocialProvider provider);
}
