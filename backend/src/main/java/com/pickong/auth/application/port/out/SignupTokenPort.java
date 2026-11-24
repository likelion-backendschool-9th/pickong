package com.pickong.auth.application.port.out;

import com.pickong.auth.application.dto.SocialProfile;
import java.time.Duration;
import java.util.Optional;

/**
 * 신규 회원의 임시 가입 토큰. 소셜 인증 직후 발급하고, 온보딩 완료(5단계)에서 검증합니다.
 * 이 토큰으로는 온보딩 API만 쓸 수 있고 Access Token으로는 쓸 수 없습니다(토큰 종류가 다름).
 * 구현: {@code auth.adapter.out.external.jwt.SignupTokenAdapter}
 */
public interface SignupTokenPort {

    /** 온보딩을 마칠 수 있는 시간. 쿠키 수명도 같습니다. */
    Duration TTL = Duration.ofMinutes(30);

    /** 소셜 계정 정보를 담은 임시 가입 토큰을 만듭니다. */
    String issue(SocialProfile profile);

    /** 토큰이 유효하면(서명 · 만료 · 종류) 담긴 소셜 계정 정보를, 아니면 빈 값을 돌려줍니다. */
    Optional<SocialProfile> verify(String signupToken);
}
