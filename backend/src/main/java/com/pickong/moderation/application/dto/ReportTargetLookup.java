package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.vo.ReportTargetType;

/**
 * 회원이 신고할 수 있는 게시물 또는 댓글.
 * text는 서버가 읽은 본문 원문입니다. null은 본문 누락이고, 빈 문자열은 빈 본문입니다.
 */
public record ReportTargetLookup(ReportTargetType targetType, long targetId, long authorId, String text) {
}
