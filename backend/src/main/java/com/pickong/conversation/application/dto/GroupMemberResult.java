package com.pickong.conversation.application.dto;

import java.util.UUID;

/**
 * 그룹 참여자 (참여 중인 사람만).
 *
 * @param name 이름. 없으면 null
 * @param me   조회한 회원인지
 */
public record GroupMemberResult(UUID publicId, String username, String name, String profileImageUrl, boolean me) {
}
