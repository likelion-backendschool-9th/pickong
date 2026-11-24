package com.pickong.moderation.application.dto;

/** 목록 카드에 보여줄 대상 미리보기. 대상이 없으면 link는 null입니다. */
public record ReportTargetPreview(String text, String imageUrl, String link, Long reportedUserId) {
}
