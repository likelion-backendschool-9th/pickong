package com.pickong.auth.application.dto;

import com.pickong.auth.domain.vo.SocialProvider;

/**
 * 소셜 인증을 마친 뒤 어디로 보낼지 정하는 결과. 웹 어댑터(OAuth2 성공 처리)가 이 값을 보고 쿠키 · 이동할 화면을 정합니다.
 * sealed라서 switch에서 경우를 빠뜨리면 컴파일 오류가 납니다.
 */
public sealed interface SocialLoginResult {

    /** 이미 가입한 회원 → 로그인 세션 발급 후 피드로 */
    record ExistingMember(Long userId) implements SocialLoginResult {
    }

    /** 처음 온 소셜 계정 → 임시 가입 토큰을 쿠키로 주고 온보딩(/signup)으로 */
    record NewMember(String signupToken) implements SocialLoginResult {
    }

    /**
     * 탈퇴 유예 중(30일 안)인 회원 → 로그인 세션 대신 탈퇴 취소 토큰을 쿠키로 주고 탈퇴 취소 화면으로.
     * 취소하면 그때 세션을 발급합니다(탈퇴 중인 계정에 로그인 세션을 만들지 않음).
     */
    record WithdrawalPending(String restoreToken) implements SocialLoginResult {
    }

    /** 가입은 돼 있지만 정지 · 탈퇴(익명화 대기 포함) 등으로 로그인할 수 없음 → 로그인 화면에 사유 안내 */
    record Unavailable() implements SocialLoginResult {
    }

    /**
     * 처음 온 소셜 계정인데, 그 이메일을 이미 다른 찜콩 계정이 쓰고 있음(1인 1계정) → 가입을 막고 안내.
     *
     * @param existingProvider 그 이메일로 연결된 계정의 제공자. "이미 카카오로 가입된 이메일이에요"처럼 안내합니다
     */
    record EmailAlreadyRegistered(SocialProvider existingProvider) implements SocialLoginResult {
    }
}
