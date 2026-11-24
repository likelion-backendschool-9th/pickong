package com.pickong.terms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.terms.domain.vo.TermsType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 약관 이력: 지금 시행 중인 버전 · 예전 동의가 아직 유효한지 (순수 도메인 규칙) */
class TermsHistoryTest {

    private static final OffsetDateTime V10 = OffsetDateTime.parse("2026-11-01T00:00:00Z");
    private static final OffsetDateTime V11 = OffsetDateTime.parse("2026-12-01T00:00:00Z");
    private static final OffsetDateTime V12 = OffsetDateTime.parse("2027-01-01T00:00:00Z");

    private static Terms privacy(long id, String version, OffsetDateTime effectiveAt, boolean requiresReconsent) {
        return Terms.builder().id(id).type(TermsType.PRIVACY).version(version).required(true)
                .effectiveAt(effectiveAt).requiresReconsent(requiresReconsent).build();
    }

    private static TermsHistory historyOf(Terms... versions) {
        return TermsHistory.group(List.of(versions)).get(TermsType.PRIVACY);
    }

    @Test
    @DisplayName("종류별로 묶고, 시행일이 가장 늦은 버전이 지금 시행 중 (입력 순서와 관계없음)")
    void currentIsLatest() {
        Terms service = Terms.builder().id(9L).type(TermsType.SERVICE).version("1.0").effectiveAt(V10).build();
        Map<TermsType, TermsHistory> histories = TermsHistory.group(List.of(
                privacy(2, "1.1", V11, false), service, privacy(1, "1.0", V10, false)));

        assertThat(histories.keySet()).containsExactly(TermsType.SERVICE, TermsType.PRIVACY);
        assertThat(histories.get(TermsType.PRIVACY).current().getVersion()).isEqualTo("1.1");
    }

    @Test
    @DisplayName("재동의 필요 버전이 없으면 어느 버전에 한 동의든 유효하다 (문구만 고친 새 버전)")
    void noReconsentKeepsOldAgreement() {
        TermsHistory history = historyOf(privacy(1, "1.0", V10, false), privacy(2, "1.1", V11, false));

        assertThat(history.accepts(V10)).isTrue();
        assertThat(history.accepts(V11)).isTrue();
        assertThat(history.hasReconsentVersion()).isFalse();
    }

    @Test
    @DisplayName("재동의 필요 버전이 시행되면 그 전 버전에 한 동의는 유효하지 않고, 그 버전부터는 유효하다")
    void reconsentInvalidatesOlder() {
        TermsHistory history = historyOf(privacy(1, "1.0", V10, false), privacy(2, "1.1", V11, true));

        assertThat(history.accepts(V10)).isFalse();
        assertThat(history.accepts(V11)).isTrue();
        assertThat(history.hasReconsentVersion()).isTrue();
    }

    @Test
    @DisplayName("1.1(재동의 필요) 뒤에 1.2(필요 없음)가 나오면 기준은 1.1: 1.1 · 1.2 동의는 유효, 1.0 동의는 무효")
    void baselineIsLastReconsentVersion() {
        TermsHistory history = historyOf(privacy(1, "1.0", V10, false), privacy(2, "1.1", V11, true), privacy(3, "1.2", V12, false));

        assertThat(history.current().getVersion()).isEqualTo("1.2");
        assertThat(history.accepts(V10)).isFalse();
        assertThat(history.accepts(V11)).isTrue();
        assertThat(history.accepts(V12)).isTrue();
    }
}
