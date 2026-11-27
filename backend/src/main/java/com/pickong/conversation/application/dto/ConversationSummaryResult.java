package com.pickong.conversation.application.dto;

import java.time.OffsetDateTime;

/**
 * 1:1 대화 목록 · 상세 한 건.
 *
 * @param lastMessage 내가 볼 수 있는 마지막 메시지. 없으면 null
 * @param unreadCount 내가 아직 읽지 않은, 상대가 보낸 메시지 수
 * @param updatedAt   마지막 메시지 시각. 메시지가 없으면 내가 참여한 시각
 */
public record ConversationSummaryResult(long id, ConversationPartner partner, MessageResult lastMessage,
                                        long unreadCount, OffsetDateTime updatedAt) {
}
