package com.pickong.terms.adapter.out.persistence;

import com.pickong.terms.adapter.out.persistence.entity.TermsJpaEntity;
import com.pickong.terms.adapter.out.persistence.entity.TermsSectionJpaEntity;
import com.pickong.terms.adapter.out.persistence.entity.UserTermsAgreementJpaEntity;
import com.pickong.terms.adapter.out.persistence.repository.TermsJpaRepository;
import com.pickong.terms.adapter.out.persistence.repository.TermsSectionJpaRepository;
import com.pickong.terms.adapter.out.persistence.repository.UserTermsAgreementJpaRepository;
import com.pickong.terms.application.dto.AgreedTerms;
import com.pickong.terms.application.port.out.TermsPersistencePort;
import com.pickong.terms.domain.model.Terms;
import com.pickong.terms.domain.vo.TermsSection;
import com.pickong.terms.domain.vo.TermsType;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/** 약관 · 약관 동의 저장소. JPA 엔티티는 이 어댑터 밖으로 내보내지 않습니다. */
@Component
public class TermsPersistenceAdapter implements TermsPersistencePort {

    private final TermsJpaRepository termsRepository;
    private final TermsSectionJpaRepository sectionRepository;
    private final UserTermsAgreementJpaRepository agreementRepository;

    public TermsPersistenceAdapter(TermsJpaRepository termsRepository, TermsSectionJpaRepository sectionRepository,
                                   UserTermsAgreementJpaRepository agreementRepository) {
        this.termsRepository = termsRepository;
        this.sectionRepository = sectionRepository;
        this.agreementRepository = agreementRepository;
    }

    @Override
    public List<Terms> findEffective(OffsetDateTime now) {
        return termsRepository.findEffective(now).stream().map(TermsJpaEntity::toDomain).toList();
    }

    @Override
    public List<TermsSection> findSections(Long termsId) {
        return sectionRepository.findByTermsIdOrderByPositionAsc(termsId).stream()
                .map(TermsSectionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void saveAgreements(Long userId, List<Long> termsIds, OffsetDateTime agreedAt) {
        agreementRepository.saveAll(termsIds.stream()
                .map(termsId -> UserTermsAgreementJpaEntity.agreed(userId, termsId, agreedAt))
                .toList());
    }

    @Override
    public void agreeIfAbsent(Long userId, Long termsId, OffsetDateTime agreedAt) {
        agreementRepository.insertIfAbsent(userId, termsId, agreedAt);
    }

    @Override
    public List<AgreedTerms> findActiveAgreements(Long userId) {
        return agreementRepository.findActive(userId).stream()
                .map(row -> new AgreedTerms(TermsType.valueOf(row.getType()), row.getVersion(), row.getTitle(), row.getEffectiveAt(), row.getAgreedAt()))
                .toList();
    }

    @Override
    public void revokeAgreements(Long userId, TermsType type, OffsetDateTime revokedAt) {
        agreementRepository.revokeActive(userId, type.name(), revokedAt);
    }
}
