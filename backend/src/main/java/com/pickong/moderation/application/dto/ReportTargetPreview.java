package com.pickong.moderation.application.dto;

/**
 * 목록 카드에 보여줄 대상 미리보기.
 * text · imageUrl은 카드용 첫 줄이고, snapshot은 신고 당시 원문입니다.
 * current.text는 게시물 · 댓글의 현재 전체 본문입니다. 회원은 표시 이름입니다.
 * 숨김이거나 삭제됐거나 대상을 찾지 못하면 link는 null입니다. 삭제·조회 불가면 current도 null입니다.
 */
public record ReportTargetPreview(
        String text,
        String imageUrl,
        String link,
        Long reportedUserId,
        ReportContent snapshot,
        ReportContent current,
        Boolean modified,
        ReportTargetVisibility visibility) {
}
