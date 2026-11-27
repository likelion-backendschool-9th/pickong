package com.pickong.conversation.application.dto;

import java.util.UUID;

/**
 * 1:1 대화 상대. 응답에는 내부 id 대신 공개 id(users.public_id)를 씁니다.
 *
 * @param name 이름. 없으면 null
 */
public record ConversationPartner(UUID publicId, String username, String name, String profileImageUrl) {
}
