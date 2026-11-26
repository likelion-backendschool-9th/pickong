package com.pickong.user.application.dto;

import java.util.UUID;

/**
 * 다른 모듈이 목록에 작성자 · 소유자를 함께 보여 줄 때 쓰는 회원 요약.
 *
 * @param id       내부 id. 다른 모듈의 user_id 조회용이며 응답으로 내보내지 않습니다
 * @param publicId 응답에 쓰는 회원 id
 * @param name     표시 이름. 없으면 null
 */
public record UserSummary(Long id, UUID publicId, String username, String name, String profileImageUrl) {
}
