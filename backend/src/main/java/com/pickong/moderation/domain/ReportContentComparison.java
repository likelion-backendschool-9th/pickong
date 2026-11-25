package com.pickong.moderation.domain;

/**
 * 신고 당시 글과 현재 글의 비교.
 * <p>전체 본문 비교는 비교용 복사본만 정규화합니다. 저장된 원문은 바꾸지 않습니다.
 * 첫 줄 비교는 카드 제목과 같은 규칙이고, 첫 줄이 없으면 판단하지 않습니다.
 * {@code false}는 이번 비교에서 같다는 뜻이지, 수정 이력이 없다는 뜻이 아닙니다.
 * Spring · JSON · DB에 의존하지 않습니다.
 */
public final class ReportContentComparison {

    private ReportContentComparison() {
    }

    /**
     * textScope가 FULL인 게시물 · 댓글 본문.
     * 둘 중 하나라도 null이면 비교하지 않습니다. 빈 문자열은 빈 본문입니다.
     */
    public static Boolean compareFull(String snapshotText, String currentText) {
        if (snapshotText == null || currentText == null) {
            return null;
        }
        return !normalize(snapshotText).equals(normalize(currentText));
    }

    /**
     * 기존 스냅샷과 회원 표시 이름.
     * 첫 줄이 없으면 null입니다. 둘째 줄 이후는 보지 않습니다.
     */
    public static Boolean compareFirstLine(String snapshotText, String currentText) {
        String snapshotLine = excerpt(snapshotText);
        String currentLine = excerpt(currentText);
        if (snapshotLine == null || currentLine == null) {
            return null;
        }
        return !snapshotLine.equals(currentLine);
    }

    /** 카드 제목과 기존 첫 줄 비교에 씁니다. 내용이 없으면 null입니다. */
    public static String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        int newline = content.indexOf('\n');
        String line = newline < 0 ? content : content.substring(0, newline);
        String trimmed = line.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** CRLF · 단독 CR을 LF로 맞추고 양끝 공백만 제거합니다. 중간 공백과 빈 줄은 남깁니다. */
    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace("\r", "\n").strip();
    }
}
