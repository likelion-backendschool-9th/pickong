package com.pickong.user.application.dto;

/**
 * 신규 회원 생성 요청 (auth 모듈의 가입 완료가 보냄).
 *
 * @param username        닉네임(= 사용자 ID) 입력값. 검증 · 소문자 변환은 user 모듈이 합니다
 * @param name            이름 입력값 (비어 있으면 이름 없음)
 * @param email           가입한 소셜 계정의 인증된 이메일 (없으면 null)
 * @param profileImageUrl 소셜 프로필 사진을 쓰기로 했을 때만 값 (없으면 null)
 */
public record RegisterUserCommand(String username, String name, String email, String profileImageUrl) {
}
