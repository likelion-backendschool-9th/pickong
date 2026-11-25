package com.pickong.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 신고 본문 비교. 저장 원문을 바꾸지 않고, 비교 결과만 돌려줍니다. */
class ReportContentComparisonTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("fullBodies")
    @DisplayName("FULL 스냅샷은 정규화한 전체 본문을 비교합니다")
    void comparesFullBody(String name, String snapshot, String current, Boolean expected) {
        assertThat(ReportContentComparison.compareFull(snapshot, current)).isEqualTo(expected);
    }

    static Stream<Arguments> fullBodies() {
        return Stream.of(
                full("전체 본문이 같으면 false", "첫 줄\n둘째 줄", "첫 줄\n둘째 줄", false),
                full("첫 줄이 바뀌면 true", "이전 첫 줄\n둘째 줄", "새 첫 줄\n둘째 줄", true),
                full("둘째 줄만 바뀌면 true", "첫 줄\n신고 당시", "첫 줄\n현재", true),
                full("마지막 줄만 바뀌면 true", "첫 줄\n가운데\n이전 끝", "첫 줄\n가운데\n새 끝", true),
                full("중간 문장이 추가되면 true", "첫 줄\n끝", "첫 줄\n추가된 문장\n끝", true),
                full("중간 문장이 삭제되면 true", "첫 줄\n빠질 문장\n끝", "첫 줄\n끝", true),
                full("CRLF와 LF만 다르면 false", "첫 줄\r\n둘째 줄", "첫 줄\n둘째 줄", false),
                full("단독 CR과 LF만 다르면 false", "첫 줄\r둘째 줄", "첫 줄\n둘째 줄", false),
                full("양끝 공백만 다르면 false", "  첫 줄\n둘째 줄  ", "첫 줄\n둘째 줄", false),
                full("중간 공백이 바뀌면 true", "첫 줄\n사이  간격", "첫 줄\n사이 간격", true),
                full("중간 빈 줄이 바뀌면 true", "첫 줄\n\n둘째 줄", "첫 줄\n둘째 줄", true),
                full("대소문자가 바뀌면 true", "Hello", "hello", true),
                full("스냅샷 본문이 null이면 null", null, "현재 본문", null),
                full("현재 본문이 null이면 null", "신고 당시", null, null),
                full("양쪽이 빈 문자열이면 false", "", "", false),
                full("스냅샷만 빈 문자열이면 true", "", "현재 본문", true),
                full("현재 본문만 빈 문자열이면 true", "신고 당시", "", true),
                full("첫 줄이 비고 둘째 줄이 다르면 true", "\n신고 당시", "\n현재", true),
                full("HTML이 다르면 true", "본문 <b>이전</b>", "본문 <b>이후</b>", true),
                full("Markdown이 다르면 true", "본문 **강조**", "본문 강조", true),
                full("같은 Markdown은 false", "본문 **강조**\n둘째", "본문 **강조**\n둘째", false),
                full("정규화 후 둘 다 빈 본문이면 false", " \r\n ", "\n", false),
                full("누락된 스냅샷과 빈 현재 본문은 null", null, "", null));
    }

    /**
     * 하위 호환: textScope가 FULL이 아닌 스냅샷은 첫 줄만 봅니다.
     * 둘째 줄 이후가 달라도 false인 것은 전체 본문을 비교하지 않기 때문입니다.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("firstLines")
    @DisplayName("기존 스냅샷은 첫 줄만 비교합니다")
    void comparesFirstLineForLegacySnapshots(String name, String snapshot, String current, Boolean expected) {
        assertThat(ReportContentComparison.compareFirstLine(snapshot, current)).isEqualTo(expected);
    }

    static Stream<Arguments> firstLines() {
        return Stream.of(
                first("첫 줄이 다르면 true", "이전 첫 줄\n같은 뒤", "새 첫 줄\n같은 뒤", true),
                first("첫 줄이 같고 둘째 줄이 달라도 false", "첫 줄\n신고 당시", "첫 줄\n현재", false),
                first("첫 줄이 비어 있으면 null", "\n둘째 줄만 있음", "현재 첫 줄\n둘째", null),
                first("스냅샷이 null이면 null", null, "현재", null),
                first("현재 본문이 null이면 null", "신고 당시", null, null),
                first("한 줄이 같으면 false", "같은 글", "같은 글", false),
                first("공백만 있는 스냅샷은 null", "   ", "현재", null),
                first("첫 줄의 CR만 다르면 false", "같은\r\n다른 뒤", "같은\n또 다른 뒤", false));
    }

    @Test
    @DisplayName("비교는 전달한 문자열을 바꾸지 않습니다")
    void comparisonDoesNotRewriteTheOriginalText() {
        String snapshot = "  첫 줄\r\n둘째  ";
        String current = "첫 줄\n둘째";

        assertThat(ReportContentComparison.compareFull(snapshot, current)).isFalse();
        assertThat(snapshot).isEqualTo("  첫 줄\r\n둘째  ");
        assertThat(current).isEqualTo("첫 줄\n둘째");
    }

    private static Arguments full(String name, String snapshot, String current, Boolean expected) {
        return Arguments.of(name, snapshot, current, expected);
    }

    private static Arguments first(String name, String snapshot, String current, Boolean expected) {
        return Arguments.of(name, snapshot, current, expected);
    }
}
