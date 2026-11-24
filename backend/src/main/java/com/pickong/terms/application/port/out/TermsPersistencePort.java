package com.pickong.terms.application.port.out;

import com.pickong.terms.application.dto.AgreedTerms;
import com.pickong.terms.domain.model.Terms;
import com.pickong.terms.domain.vo.TermsSection;
import com.pickong.terms.domain.vo.TermsType;
import java.time.OffsetDateTime;
import java.util.List;

/** 약관 · 약관 동의 저장소 포트. 구현: {@code terms.adapter.out.persistence.TermsPersistenceAdapter} */
public interface TermsPersistencePort {

    /**
     * 이미 시행된 약관 버전 전체 (시행일이 now 이전). 종류마다 가장 늦은 것이 지금 시행 중인 버전이고,
     * 예전 버전들은 예전 동의가 아직 유효한지 판단하는 데 씁니다({@code TermsHistory}).
     */
    List<Terms> findEffective(OffsetDateTime now);

    /** 약관 버전 하나의 조항을 순서대로 */
    List<TermsSection> findSections(Long termsId);

    /**
     * 동의 기록을 남깁니다. (user_terms_agreements는 '추가만' 하는 이력 테이블이라 domain 모델 없이 원시값으로 받음, blueprint 4.2)
     */
    void saveAgreements(Long userId, List<Long> termsIds, OffsetDateTime agreedAt);

    /**
     * 이 약관 버전에 철회하지 않은 동의가 없을 때만 동의 기록을 하나 남깁니다.
     * 동시에 여러 번 불려도 기록은 하나입니다. (DB 부분 유니크 인덱스가 보장, 이미 있으면 아무것도 하지 않음)
     */
    void agreeIfAbsent(Long userId, Long termsId, OffsetDateTime agreedAt);

    /** 철회하지 않은 동의 기록 전체 (버전과 관계없이). 최근 동의가 먼저 옵니다 */
    List<AgreedTerms> findActiveAgreements(Long userId);

    /** 이 종류의 철회하지 않은 동의 기록에 철회 시각을 적습니다. (지우지 않고 이력으로 남김) */
    void revokeAgreements(Long userId, TermsType type, OffsetDateTime revokedAt);
}
