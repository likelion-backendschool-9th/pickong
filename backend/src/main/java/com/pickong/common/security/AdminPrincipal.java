package com.pickong.common.security;

/**
 * 인증된 관리자. 회원 {@link LoginUser}와 다른 계정입니다.
 * <p>
 * 관리자 로그인 필터는 아직 없습니다. 컨트롤러는 이 주체의 id만 심사자로 쓰고, 요청 본문의 id는 받지 않습니다.
 *
 * @param adminId admins.id (내부 BIGINT id)
 */
public record AdminPrincipal(Long adminId) {
}
