package com.pickong.auth.application.port.out;

import com.pickong.auth.domain.vo.SocialProvider;
import java.time.Duration;
import java.util.Optional;

/**
 * 계정 연결 토큰. 로그인한 회원이 '연결하기'를 누른 순간(CSRF 보호된 POST)에 발급하고, OAuth 시작 주소에 붙여 보냅니다.
 * 시작할 때 이 토큰의 회원과 로그인 쿠키의 회원이 같아야만 연결 모드로 진행합니다.
 * 구현: {@code auth.adapter.out.external.jwt.LinkTokenAdapter}
 */
public interface LinkTokenPort {

    /** 버튼을 누르고 제공자 화면으로 넘어가기까지 넉넉한 시간. 짧을수록 주소가 새어도 쓸 수 있는 시간이 줄어듭니다. */
    Duration TTL = Duration.ofMinutes(5);

    /** 회원 id와 제공자를 담은 연결 토큰을 만듭니다. */
    String issue(Long userId, SocialProvider provider);

    /** 토큰이 유효하고 같은 제공자용이면 회원 id를, 아니면 빈 값을 돌려줍니다. */
    Optional<Long> verify(String linkToken, SocialProvider provider);
}
