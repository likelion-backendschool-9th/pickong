package com.pickong.moderation.domain.model;

/**
 * 신고 당시 전체 본문 스냅샷. 비교용 정규화를 하지 않고, 이미지는 담지 않습니다.
 * JSON 직렬화는 이 객체가 하지 않습니다.
 */
public record ReportSnapshot(String text, String imageUrl, long reportedUserId, String textScope) {

    public static final String FULL = "FULL";

    public ReportSnapshot {
        if (reportedUserId <= 0) {
            throw new IllegalArgumentException("작성자 id가 필요합니다.");
        }
        if (!FULL.equals(textScope)) {
            throw new IllegalArgumentException("전체 본문 스냅샷만 만듭니다.");
        }
        if (imageUrl != null) {
            throw new IllegalArgumentException("이미지는 스냅샷에 넣지 않습니다.");
        }
    }

    /** text가 null이면 본문 누락이고, 빈 문자열이면 빈 본문입니다. */
    public static ReportSnapshot full(String text, long reportedUserId) {
        return new ReportSnapshot(text, null, reportedUserId, FULL);
    }
}
