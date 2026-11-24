package com.pickong.terms.domain.model;

import com.pickong.terms.domain.vo.TermsType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 약관 종류 하나의 '이미 시행된' 버전들 (시행일 순). 지금 시행 중인 버전과, 예전 동의가 아직 유효한지를 판단합니다.
 * <p>
 * <b>동의 유효 규칙:</b> 그 종류에서 가장 최근에 시행된 '재동의 필요' 버전을 기준으로 삼아,
 * 회원이 동의한 버전이 기준 버전과 같거나 그 뒤에 시행된 것이면 유효합니다. '재동의 필요' 버전이 없으면 어떤 버전에 한 동의든 유효합니다.
 * <pre>
 * 1.0 ── 1.1(재동의 필요) ── 1.2(필요 없음, 지금)
 * 1.0에 동의 → 다시 동의해야 함 / 1.1 · 1.2에 동의 → 유효
 * </pre>
 * 문구만 고친 버전(재동의 필요 없음)이 나올 때마다 모든 회원에게 다시 묻지 않기 위해, 기준을 '지금 버전'이 아니라
 * '마지막 재동의 필요 버전'으로 둡니다.
 */
public final class TermsHistory {

    private final List<Terms> versions;

    private TermsHistory(List<Terms> versions) {
        this.versions = versions;
    }

    /**
     * 시행된 약관 전체를 종류별로 묶습니다. 같은 종류 안에서는 시행일 순(같으면 id 순)으로 정렬합니다.
     *
     * @param effective 시행일이 지난 약관 버전들 (순서 상관없음)
     */
    public static Map<TermsType, TermsHistory> group(List<Terms> effective) {
        Map<TermsType, List<Terms>> byType = new EnumMap<>(TermsType.class);
        for (Terms terms : effective) {
            byType.computeIfAbsent(terms.getType(), type -> new ArrayList<>()).add(terms);
        }
        Map<TermsType, TermsHistory> histories = new EnumMap<>(TermsType.class);
        byType.forEach((type, list) -> {
            list.sort(Comparator.comparing(Terms::getEffectiveAt).thenComparing(Terms::getId));
            histories.put(type, new TermsHistory(List.copyOf(list)));
        });
        return histories;
    }

    /** 지금 시행 중인 버전 (가장 늦게 시행된 것) */
    public Terms current() {
        return versions.getLast();
    }

    /**
     * 이 시행일의 버전에 한 동의가 아직 유효한지.
     *
     * @param agreedVersionEffectiveAt 회원이 동의한 약관 버전의 시행일
     */
    public boolean accepts(OffsetDateTime agreedVersionEffectiveAt) {
        return reconsentBaseline()
                .map(baseline -> !agreedVersionEffectiveAt.isBefore(baseline.getEffectiveAt()))
                .orElse(true);
    }

    /**
     * '재동의 필요' 버전이 하나라도 시행됐는지. 이때만 기존 회원에게 동의를 받습니다.
     * (재동의 필요 버전이 없으면, 동의 기록이 없는 회원도 막지 않습니다. 새 필수 약관 종류를 추가할 때는 재동의 필요로 넣어야 기존 회원에게 묻습니다)
     */
    public boolean hasReconsentVersion() {
        return reconsentBaseline().isPresent();
    }

    /** 가장 최근에 시행된 '재동의 필요' 버전. 없으면 빈 값 */
    private Optional<Terms> reconsentBaseline() {
        for (int i = versions.size() - 1; i >= 0; i--) {
            if (versions.get(i).isRequiresReconsent()) {
                return Optional.of(versions.get(i));
            }
        }
        return Optional.empty();
    }
}
