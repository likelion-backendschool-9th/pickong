package com.pickong.terms.application.port.out;

import com.pickong.terms.application.dto.AgreedTerms;
import com.pickong.terms.domain.model.Terms;
import com.pickong.terms.domain.vo.TermsSection;
import com.pickong.terms.domain.vo.TermsType;
import java.time.OffsetDateTime;
import java.util.List;

/** 약관 · 약관 동의 저장소 포트. 구현: {@code terms.adapter.out.persistence.TermsPersistenceAdapter} */
public interface TermsPersistencePort {

    /** 지금 시행 중인 약관: 종류마다 시행일(effective_at)이 now 이전인 것 중 가장 최근 버전 하나씩 */
    List<Terms> findCurrent(OffsetDateTime now);

    /** 약관 버전 하나의 조항을 순서대로 */
    List<TermsSection> findSections(Long termsId);

    /**
     * 동의 기록을 남깁니다. (user_terms_agreements는 '추가만' 하는 이력 테이블이라 domain 모델 없이 원시값으로 받음, blueprint 4.2)
     */
    void saveAgreements(Long userId, List<Long> termsIds, OffsetDateTime agreedAt);

    /** 철회하지 않은 동의 기록 전체 (버전과 관계없이). 최근 동의가 먼저 옵니다 */
    List<AgreedTerms> findActiveAgreements(Long userId);

    /** 이 종류의 철회하지 않은 동의 기록에 철회 시각을 적습니다. (지우지 않고 이력으로 남김) */
    void revokeAgreements(Long userId, TermsType type, OffsetDateTime revokedAt);
}
