package com.pickong.user.application.dto;

import java.util.Optional;

/**
 * 프로필 수정 요청 (PATCH /me). <b>보낸 항목만 바꿉니다.</b>
 * <p>
 * 각 항목은 세 가지 상태입니다.
 * <ul>
 *   <li>{@code null} — 요청에 없음 → 그대로 둠</li>
 *   <li>{@code Optional.empty()} — {@code null}을 보냄 → 지움 (아이디는 지울 수 없어 INVALID_USERNAME)</li>
 *   <li>{@code Optional.of(값)} — 그 값으로 바꿈</li>
 * </ul>
 * 필드에 Optional을 두는 것은 보통 피하지만, '없음'과 '지움'을 구분해야 하는 이 경우에만 씁니다.
 */
public record UpdateProfileCommand(
        Optional<String> username,
        Optional<String> name,
        Optional<String> bio,
        Optional<String> region) {
}
