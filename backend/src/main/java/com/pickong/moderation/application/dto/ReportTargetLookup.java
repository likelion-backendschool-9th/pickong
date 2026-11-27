package com.pickong.moderation.application.dto;

import com.pickong.moderation.domain.vo.ReportTargetType;

/**
 * 회원이 신고할 수 있는 게시물 또는 댓글.
 * text는 서버가 읽은 본문 원문입니다. null은 본문 누락이고, 빈 문자열은 빈 본문입니다.
 *
 * @param feedId 열람 가능 여부를 feed 모듈에 물어볼 게시물 id. 게시물이면 targetId와 같고, 댓글이면 상위 게시물 id
 */
public record ReportTargetLookup(ReportTargetType targetType, long targetId, long authorId, String text, long feedId) {
}
