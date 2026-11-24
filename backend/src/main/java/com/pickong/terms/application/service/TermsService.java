package com.pickong.terms.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.terms.application.dto.AgreedTerms;
import com.pickong.terms.application.dto.AgreementStatus;
import com.pickong.terms.application.dto.PendingTerms;
import com.pickong.terms.application.dto.TermsDocument;
import com.pickong.terms.application.port.in.TermsUseCase;
import com.pickong.terms.application.port.out.TermsPersistencePort;
import com.pickong.terms.domain.TermsErrorCode;
import com.pickong.terms.domain.model.Terms;
import com.pickong.terms.domain.model.TermsHistory;
import com.pickong.terms.domain.vo.TermsType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermsService implements TermsUseCase {

    private final TermsPersistencePort termsPersistencePort;
    private final Clock clock;

    public TermsService(TermsPersistencePort termsPersistencePort, Clock clock) {
        this.termsPersistencePort = termsPersistencePort;
        this.clock = clock;
    }

    /**
     * 순서: 받은 종류를 enum으로 바꿈(모르면 오류) → 지금 시행 중인 약관 조회 → 필수 약관이 모두 있는지 확인 → 동의한 것만 기록.
     * 필수 여부는 코드가 아니라 DB(terms.is_required)를 따르므로, 나중에 약관이 바뀌어도 코드를 고치지 않아도 됩니다.
     */
    @Override
    @Transactional
    public void agreeOnSignup(Long userId, Set<String> agreedTypes) {
        Set<TermsType> agreed = toTypes(agreedTypes);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Terms> current = current(histories(now));

        boolean missingRequired = current.stream().anyMatch(terms -> terms.isRequired() && !agreed.contains(terms.getType()));
        if (missingRequired) {
            throw new BusinessException(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        List<Long> agreedTermsIds = current.stream()
                .filter(terms -> agreed.contains(terms.getType()))
                .map(Terms::getId)
                .toList();
        termsPersistencePort.saveAgreements(userId, agreedTermsIds, now);
    }

    /** 경로의 종류를 enum으로 바꾸고(모르면 404), 그 종류의 시행 중인 버전과 조항을 함께 돌려줍니다. */
    @Override
    @Transactional(readOnly = true)
    public TermsDocument getCurrentTerms(String type) {
        TermsType termsType = parseType(type).orElseThrow(() -> new BusinessException(TermsErrorCode.TERMS_NOT_FOUND));
        TermsHistory history = histories(OffsetDateTime.now(clock)).get(termsType);
        if (history == null) {
            throw new BusinessException(TermsErrorCode.TERMS_NOT_FOUND);
        }
        Terms terms = history.current();
        return new TermsDocument(terms.getType(), terms.getTitle(), terms.isRequired(), terms.getVersion(),
                terms.getEffectiveAt(), termsPersistencePort.findSections(terms.getId()));
    }

    /**
     * 시행 중인 약관마다, 그 종류의 <b>아직 유효한</b> 동의 중 가장 최근 것을 붙입니다.
     * 예전 버전에 한 동의도 새 버전이 '재동의 필요'가 아니면 유효해서 '동의함 · 동의한 버전(예전) · 그 버전의 제목'으로 보여 줍니다.
     * 재동의가 필요한 새 버전이 시행됐다면 예전 동의는 '동의하지 않음'으로 보입니다.
     * 제목도 동의한 버전에서 가져와야 '1.0 · 1.1의 제목'처럼 실제로 없던 조합이 나오지 않습니다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AgreementStatus> getMyAgreements(Long userId) {
        return snapshot(userId).statuses();
    }

    /**
     * 동의 받을 약관: 필수 약관은 재동의 필요 버전이 시행됐는데 유효한 동의가 없을 때, 선택 약관은 예전에 동의했는데 그 동의가 더 이상 유효하지 않을 때만.
     * (선택 약관에 한 번도 동의하지 않은 회원에게는 묻지 않습니다)
     */
    @Override
    @Transactional(readOnly = true)
    public List<PendingTerms> getPendingTerms(Long userId) {
        return snapshot(userId).pending();
    }

    /** 요청마다 불리므로(재동의 필터) 조회는 시행된 약관 · 내 동의 두 번뿐입니다. */
    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingRequiredTerms(Long userId) {
        return snapshot(userId).pending().stream().anyMatch(PendingTerms::required);
    }

    /**
     * 순서: 종류 확인(모르면 400) → 동의 받을 필수 약관이 모두 들어 있는지 확인(빠지면 400, 아무것도 저장 안 함)
     * → 보낸 종류마다 유효한 동의가 없으면 시행 중인 버전으로 기록 → 동의 받을 선택 약관 중 보내지 않은 것은 예전 동의를 철회
     * (거절을 기록해 두어야 로그인할 때마다 다시 묻지 않습니다).
     * 한 트랜잭션이라 일부만 저장되지 않고, 같은 요청이 동시에 와도 기록은 하나입니다(부분 유니크 인덱스).
     */
    @Override
    @Transactional
    public List<AgreementStatus> agreeToPendingTerms(Long userId, Set<String> agreedTypes) {
        Set<TermsType> agreed = EnumSet.noneOf(TermsType.class);
        for (String value : agreedTypes == null ? Set.<String>of() : agreedTypes) {
            agreed.add(parseType(value).orElseThrow(() -> new BusinessException(TermsErrorCode.UNKNOWN_TERMS)));
        }
        ConsentSnapshot snapshot = snapshot(userId);
        List<PendingTerms> pending = snapshot.pending();
        if (pending.stream().anyMatch(terms -> terms.required() && !agreed.contains(terms.type()))) {
            throw new BusinessException(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        for (TermsType type : agreed) {
            TermsHistory history = snapshot.histories().get(type);
            if (history == null) {
                throw new BusinessException(TermsErrorCode.UNKNOWN_TERMS);
            }
            if (snapshot.validAgreement(type).isEmpty()) {
                termsPersistencePort.agreeIfAbsent(userId, history.current().getId(), now);
            }
        }
        for (PendingTerms terms : pending) {
            if (!terms.required() && !agreed.contains(terms.type())) {
                termsPersistencePort.revokeAgreements(userId, terms.type(), now);
            }
        }
        return getMyAgreements(userId);
    }

    /**
     * 순서: 종류 확인(모르면 400) → 필수 약관이면 거절 → 동의: 유효한 동의가 없으면 시행 중인 버전으로 새 기록
     * / 철회: 그 종류의 동의 기록(모든 버전)에 철회 시각 → 바뀐 상태 전체를 돌려줌.
     * 재동의가 필요한 새 버전이 시행된 뒤 스위치를 켜면 예전 동의가 유효하지 않으므로 새 버전으로 기록됩니다.
     * 동의 요청 두 개가 동시에 와서 둘 다 '동의 없음'을 확인해도, 저장은 DB 부분 유니크 인덱스로 한 번만 되므로 기록은 하나입니다.
     * 필수 약관은 동의 · 철회 모두 막습니다. 동의는 재동의 화면(POST)에서만, 해지는 탈퇴로만 하기 때문입니다.
     */
    @Override
    @Transactional
    public List<AgreementStatus> updateAgreement(Long userId, String type, boolean agreed) {
        TermsType termsType = parseType(type).orElseThrow(() -> new BusinessException(TermsErrorCode.UNKNOWN_TERMS));
        ConsentSnapshot snapshot = snapshot(userId);
        TermsHistory history = snapshot.histories().get(termsType);
        if (history == null) {
            throw new BusinessException(TermsErrorCode.UNKNOWN_TERMS);
        }
        if (history.current().isRequired()) {
            throw new BusinessException(TermsErrorCode.REQUIRED_TERMS_CANNOT_BE_REVOKED);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (agreed) {
            if (snapshot.validAgreement(termsType).isEmpty()) {
                termsPersistencePort.agreeIfAbsent(userId, history.current().getId(), now);
            }
        } else {
            termsPersistencePort.revokeAgreements(userId, termsType, now);
        }
        return getMyAgreements(userId);
    }

    /** 시행된 약관을 종류별 이력으로 */
    private Map<TermsType, TermsHistory> histories(OffsetDateTime now) {
        return TermsHistory.group(termsPersistencePort.findEffective(now));
    }

    /** 종류마다 지금 시행 중인 버전 (종류 순서) */
    private static List<Terms> current(Map<TermsType, TermsHistory> histories) {
        return histories.values().stream().map(TermsHistory::current).toList();
    }

    /** 한 회원의 동의 판단에 필요한 것을 한 번에 읽어 둡니다: 시행된 약관 이력 + 철회하지 않은 동의(최근 순) */
    private ConsentSnapshot snapshot(Long userId) {
        return new ConsentSnapshot(histories(OffsetDateTime.now(clock)), termsPersistencePort.findActiveAgreements(userId));
    }

    /**
     * 한 회원의 동의 상태 계산 (DB를 더 읽지 않음).
     *
     * @param active 철회하지 않은 동의 기록, 최근 동의가 먼저
     */
    private record ConsentSnapshot(Map<TermsType, TermsHistory> histories, List<AgreedTerms> active) {

        /** 이 종류의 아직 유효한 동의 중 가장 최근 것 */
        Optional<AgreedTerms> validAgreement(TermsType type) {
            TermsHistory history = histories.get(type);
            return active.stream()
                    .filter(agreement -> agreement.type() == type)
                    .filter(agreement -> history != null && history.accepts(agreement.versionEffectiveAt()))
                    .findFirst();
        }

        /** 예전 버전에 한 동의가 남아 있지만(철회하지 않음) 더 이상 유효하지 않음 */
        boolean hasOutdatedAgreement(TermsType type) {
            return validAgreement(type).isEmpty() && active.stream().anyMatch(agreement -> agreement.type() == type);
        }

        List<AgreementStatus> statuses() {
            return current(histories).stream()
                    .map(terms -> validAgreement(terms.getType())
                            .map(agreed -> new AgreementStatus(terms.getType(), agreed.title(), terms.isRequired(), agreed.version(), true, agreed.agreedAt()))
                            .orElseGet(() -> new AgreementStatus(terms.getType(), terms.getTitle(), terms.isRequired(), terms.getVersion(), false, null)))
                    .toList();
        }

        /**
         * 필수 약관: 재동의 필요 버전이 시행됐는데 유효한 동의가 없음. 선택 약관: 예전 동의가 남아 있지만 더 이상 유효하지 않음.
         * 재동의 필요 버전이 없는 종류는 동의 기록이 없어도 묻지 않습니다(정책: '재동의가 필요한' 약관만 받음).
         */
        List<PendingTerms> pending() {
            return current(histories).stream()
                    .filter(terms -> terms.isRequired()
                            ? histories.get(terms.getType()).hasReconsentVersion() && validAgreement(terms.getType()).isEmpty()
                            : hasOutdatedAgreement(terms.getType()))
                    .map(terms -> new PendingTerms(terms.getType(), terms.getTitle(), terms.isRequired(), terms.getVersion()))
                    .toList();
        }
    }

    /** 대소문자를 가리지 않고 enum으로 바꿉니다. 모르는 값 · null이면 빈 값 */
    private static Optional<TermsType> parseType(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(TermsType.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Set<TermsType> toTypes(Set<String> values) {
        Set<TermsType> types = EnumSet.noneOf(TermsType.class);
        if (values == null) {
            return types;
        }
        for (String value : values) {
            try {
                types.add(TermsType.valueOf(value));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BusinessException(TermsErrorCode.UNKNOWN_TERMS);
            }
        }
        return types;
    }
}
