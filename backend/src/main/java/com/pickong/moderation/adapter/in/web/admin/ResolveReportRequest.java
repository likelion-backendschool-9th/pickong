package com.pickong.moderation.adapter.in.web.admin;

/** 신고 처리 요청. 관리자 id는 받지 않습니다. */
public record ResolveReportRequest(String action, String memo, String sanctionType) {
}
