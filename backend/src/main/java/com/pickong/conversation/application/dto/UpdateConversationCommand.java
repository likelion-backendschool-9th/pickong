package com.pickong.conversation.application.dto;

/**
 * PATCH /conversations/{id}. 보낸 항목만 바꿉니다.
 *
 * @param title 그룹 이름 (그룹만, 멤버 누구나). null이면 그대로
 * @param muted 내 알림 끄기. null이면 그대로
 */
public record UpdateConversationCommand(String title, Boolean muted) {
}
