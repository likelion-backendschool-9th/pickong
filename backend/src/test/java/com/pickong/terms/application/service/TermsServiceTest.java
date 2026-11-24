package com.pickong.terms.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pickong.common.error.BusinessException;
import com.pickong.terms.application.port.out.TermsPersistencePort;
import com.pickong.terms.domain.TermsErrorCode;
import com.pickong.terms.domain.model.Terms;
import com.pickong.terms.domain.vo.TermsType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 가입 약관 동의 규칙 단위 테스트 (DB 없이 Mockito). */
@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    @Mock
    TermsPersistencePort termsPersistencePort;

    TermsService termsService;

    @BeforeEach
    void setUp() {
        termsService = new TermsService(termsPersistencePort, Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC));
    }

    private static Terms terms(long id, TermsType type, boolean required) {
        return Terms.builder().id(id).type(type).version("1.0").required(required).build();
    }

    private void currentTermsAre(Terms... terms) {
        given(termsPersistencePort.findCurrent(any())).willReturn(List.of(terms));
    }

    @Test
    @DisplayName("필수 약관만 동의하면 동의한 약관만 지금 시행 중인 버전으로 기록한다")
    void recordsOnlyAgreed() {
        currentTermsAre(terms(1, TermsType.SERVICE, true), terms(2, TermsType.PRIVACY, true), terms(3, TermsType.MARKETING, false));

        termsService.agreeOnSignup(7L, Set.of("SERVICE", "PRIVACY"));

        verify(termsPersistencePort).saveAgreements(eq(7L), eq(List.of(1L, 2L)), any());
    }

    @Test
    @DisplayName("선택 약관(마케팅)까지 동의하면 함께 기록한다")
    void recordsOptional() {
        currentTermsAre(terms(1, TermsType.SERVICE, true), terms(2, TermsType.PRIVACY, true), terms(3, TermsType.MARKETING, false));

        termsService.agreeOnSignup(7L, Set.of("SERVICE", "PRIVACY", "MARKETING"));

        verify(termsPersistencePort).saveAgreements(eq(7L), eq(List.of(1L, 2L, 3L)), any());
    }

    @Test
    @DisplayName("필수 약관이 하나라도 빠지면 REQUIRED_TERMS_NOT_AGREED이고 아무것도 기록하지 않는다")
    void missingRequired() {
        currentTermsAre(terms(1, TermsType.SERVICE, true), terms(2, TermsType.PRIVACY, true), terms(3, TermsType.MARKETING, false));

        assertThatThrownBy(() -> termsService.agreeOnSignup(7L, Set.of("SERVICE", "MARKETING")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
        verify(termsPersistencePort, never()).saveAgreements(anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("모르는 약관 종류는 UNKNOWN_TERMS")
    void unknownType() {
        assertThatThrownBy(() -> termsService.agreeOnSignup(7L, Set.of("SERVICE", "PRIVACY", "LOCATION")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TermsErrorCode.UNKNOWN_TERMS);
    }
}
