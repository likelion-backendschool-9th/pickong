package com.pickong.auth.application.dto;

/**
 * 가입 완료 결과.
 *
 * @param userId   새 회원 id (세션 발급에 씀)
 * @param username 저장된 닉네임 (소문자로 맞춘 값)
 */
public record SignUpResult(Long userId, String username) {
}
