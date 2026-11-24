package com.pickong.moderation.application.dto;

/** 신고 대상의 글과 이미지. 없는 값은 null이며 다른 시점의 내용으로 채우지 않습니다. */
public record ReportContent(String text, String imageUrl) {
}
