package com.pickong.terms.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.terms.application.dto.AgreedTerms;
import com.pickong.terms.application.dto.AgreementStatus;
import com.pickong.terms.application.dto.TermsDocument;
import com.pickong.terms.application.port.in.TermsUseCase;
import com.pickong.terms.application.port.out.TermsPersistencePort;
import com.pickong.terms.domain.TermsErrorCode;
import com.pickong.terms.domain.model.Terms;
import com.pickong.terms.domain.vo.TermsType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumMap;
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
        List<Terms> current = termsPersistencePort.findCurrent(now);

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
        Terms terms = findCurrent(termsType).orElseThrow(() -> new BusinessException(TermsErrorCode.TERMS_NOT_FOUND));
        return new TermsDocument(terms.getType(), terms.getTitle(), terms.isRequired(), terms.getVersion(),
                terms.getEffectiveAt(), termsPersistencePort.findSections(terms.getId()));
    }

    /**
     * 시행 중인 약관마다, 그 종류의 철회하지 않은 동의 중 가장 최근 것을 붙입니다.
     * 예전 버전에 동의했고 새 버전이 나왔다면 '동의함 · 동의한 버전(예전) · 그 버전의 제목'으로 보여 줍니다. (새 버전 재동의 흐름은 아직 없음)
     * 제목도 동의한 버전에서 가져와야 '1.0 · 1.1의 제목'처럼 실제로 없던 조합이 나오지 않습니다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AgreementStatus> getMyAgreements(Long userId) {
        Map<TermsType, AgreedTerms> latest = new EnumMap<>(TermsType.class);
        for (AgreedTerms agreement : termsPersistencePort.findActiveAgreements(userId)) {
            latest.putIfAbsent(agreement.type(), agreement); // 최근 동의가 먼저 오므로 첫 번째만 남김
        }

        return termsPersistencePort.findCurrent(OffsetDateTime.now(clock)).stream()
                .sorted(Comparator.comparing(Terms::getType))
                .map(terms -> {
                    AgreedTerms agreed = latest.get(terms.getType());
                    return agreed == null
                            ? new AgreementStatus(terms.getType(), terms.getTitle(), terms.isRequired(), terms.getVersion(), false, null)
                            : new AgreementStatus(terms.getType(), agreed.title(), terms.isRequired(), agreed.version(), true, agreed.agreedAt());
                })
                .toList();
    }

    /**
     * 순서: 종류 확인(모르면 400) → 필수 약관이면 거절 → 동의: 이미 동의 중이 아니면 시행 중인 버전으로 새 기록
     * / 철회: 그 종류의 동의 기록에 철회 시각 → 바뀐 상태 전체를 돌려줌.
     * 동의 요청 두 개가 동시에 와서 둘 다 '동의 중 아님'을 확인해도, 저장은 DB 부분 유니크 인덱스로 한 번만 되므로 기록은 하나입니다.
     * 필수 약관은 동의 · 철회 모두 막습니다. 가입할 때 반드시 동의했고, 해지는 탈퇴로만 하기 때문입니다.
     */
    @Override
    @Transactional
    public List<AgreementStatus> updateAgreement(Long userId, String type, boolean agreed) {
        TermsType termsType = parseType(type).orElseThrow(() -> new BusinessException(TermsErrorCode.UNKNOWN_TERMS));
        Terms terms = findCurrent(termsType).orElseThrow(() -> new BusinessException(TermsErrorCode.UNKNOWN_TERMS));
        if (terms.isRequired()) {
            throw new BusinessException(TermsErrorCode.REQUIRED_TERMS_CANNOT_BE_REVOKED);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (agreed) {
            boolean alreadyAgreed = termsPersistencePort.findActiveAgreements(userId).stream()
                    .anyMatch(agreement -> agreement.type() == termsType);
            if (!alreadyAgreed) {
                termsPersistencePort.agreeIfAbsent(userId, terms.getId(), now);
            }
        } else {
            termsPersistencePort.revokeAgreements(userId, termsType, now);
        }
        return getMyAgreements(userId);
    }

    /** 종류 하나의 지금 시행 중인 버전 */
    private Optional<Terms> findCurrent(TermsType type) {
        return termsPersistencePort.findCurrent(OffsetDateTime.now(clock)).stream()
                .filter(terms -> terms.getType() == type)
                .findFirst();
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
