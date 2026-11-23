package com.pickong.user.application.dto;

/**
 * 만들어진 회원.
 *
 * @param userId   users.id
 * @param username 저장된 닉네임 (소문자로 맞춘 값)
 */
public record RegisteredUser(Long userId, String username) {
}
