package com.pickong.moderation.adapter.in.web.request;

/** 회원 신고 요청. 본문 · 작성자 · 신고자 id는 필드가 아니라서 저장하지 않습니다. */
public record CreateReportRequest(String targetType, Long targetId, String reason, String detail) {
}
