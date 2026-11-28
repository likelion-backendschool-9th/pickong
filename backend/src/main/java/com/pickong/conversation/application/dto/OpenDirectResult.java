package com.pickong.conversation.application.dto;

/**
 * 1:1 대화방 열기 결과.
 *
 * @param created 이번 요청으로 새로 만들었으면 true (이미 있던 대화방이면 false)
 */
public record OpenDirectResult(ConversationSummaryResult conversation, boolean created) {
}
