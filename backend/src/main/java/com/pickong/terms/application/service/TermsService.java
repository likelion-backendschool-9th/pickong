package com.pickong.terms.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.terms.application.port.in.TermsUseCase;
import com.pickong.terms.application.port.out.TermsPersistencePort;
import com.pickong.terms.domain.TermsErrorCode;
import com.pickong.terms.domain.model.Terms;
import com.pickong.terms.domain.vo.TermsType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
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
