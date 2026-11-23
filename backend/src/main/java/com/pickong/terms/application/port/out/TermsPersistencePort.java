package com.pickong.terms.application.port.out;

import com.pickong.terms.domain.model.Terms;
import java.time.OffsetDateTime;
import java.util.List;

/** 약관 · 약관 동의 저장소 포트. 구현: {@code terms.adapter.out.persistence.TermsPersistenceAdapter} */
public interface TermsPersistencePort {

    /** 지금 시행 중인 약관: 종류마다 시행일(effective_at)이 now 이전인 것 중 가장 최근 버전 하나씩 */
    List<Terms> findCurrent(OffsetDateTime now);

    /**
     * 동의 기록을 남깁니다. (user_terms_agreements는 '추가만' 하는 이력 테이블이라 domain 모델 없이 원시값으로 받음, blueprint 4.2)
     */
    void saveAgreements(Long userId, List<Long> termsIds, OffsetDateTime agreedAt);
}
