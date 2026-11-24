package com.pickong.common.security;

/**
 * 로그인한 회원. 검증된 Access Token에서 만들어지며, 컨트롤러에서는 {@link CurrentUser}로 받습니다.
 *
 * @param userId users.id (내부 BIGINT id)
 */
public record LoginUser(Long userId) {
}
