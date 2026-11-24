package com.pickong.auth.application.dto;

/** 탈퇴를 취소한 회원. 웹 어댑터가 이 회원으로 로그인 세션을 발급합니다. */
public record RestoredAccount(Long userId, String username) {
}
