package com.pickong.conversation.adapter.in.web.response;

/** GET /conversations/unread-count — {@code {"count": 3}} (하단바 배지) */
public record UnreadCountResponse(long count) {
}
