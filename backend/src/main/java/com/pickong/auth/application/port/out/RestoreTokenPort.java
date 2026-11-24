package com.pickong.auth.application.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * 탈퇴 취소 토큰: 탈퇴 유예 중인 회원이 소셜 로그인했을 때, 로그인 세션 대신 주는 짧은 서명 토큰(회원 id).
 * 탈퇴 중인 계정에 로그인 세션을 만들지 않으려고 따로 둡니다. 구현: {@code RestoreTokenAdapter}
 */
public interface RestoreTokenPort {

    /** 탈퇴 취소 화면을 보고 결정하기까지 넉넉한 시간. 지나면 다시 로그인하면 새로 발급됩니다. */
    Duration TTL = Duration.ofMinutes(10);

    String issue(Long userId);

    /** 서명 · 만료 · 종류가 맞으면 회원 id, 아니면 빈 값 */
    Optional<Long> verify(String restoreToken);
}
