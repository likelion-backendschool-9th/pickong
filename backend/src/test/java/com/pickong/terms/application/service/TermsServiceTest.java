package com.pickong.terms.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.terms.application.dto.AgreedTerms;
import com.pickong.terms.application.dto.AgreementStatus;
import com.pickong.terms.application.dto.TermsDocument;
import com.pickong.terms.application.port.out.TermsPersistencePort;
import com.pickong.terms.domain.TermsErrorCode;
import com.pickong.terms.domain.model.Terms;
import com.pickong.terms.domain.vo.TermsSection;
import com.pickong.terms.domain.vo.TermsType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 약관 동의 규칙(가입 · 설정) · 약관 전문 조회 단위 테스트 (DB 없이 Mockito). */
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

    // ── 약관 전문 ───────────────────────────────────────────

    private static ErrorCode errorOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    private void allCurrent() {
        currentTermsAre(terms(3, TermsType.MARKETING, false), terms(2, TermsType.PRIVACY, true), terms(1, TermsType.SERVICE, true));
    }

    @Test
    @DisplayName("약관 전문: 경로의 소문자 종류(service)로 시행 중인 버전과 조항을 돌려준다")
    void currentTermsCaseInsensitive() {
        allCurrent();
        given(termsPersistencePort.findSections(1L)).willReturn(List.of(new TermsSection("제1조 (목적)", "...")));

        TermsDocument document = termsService.getCurrentTerms("service");

        assertThat(document.type()).isEqualTo(TermsType.SERVICE);
        assertThat(document.sections()).extracting(TermsSection::heading).containsExactly("제1조 (목적)");
    }

    @Test
    @DisplayName("약관 전문: 모르는 종류는 TERMS_NOT_FOUND (404)")
    void unknownTermsDocument() {
        assertThatThrownBy(() -> termsService.getCurrentTerms("location"))
                .isInstanceOf(BusinessException.class)
                .extracting(TermsServiceTest::errorOf)
                .isEqualTo(TermsErrorCode.TERMS_NOT_FOUND);
    }

    // ── 내 동의 상태 ────────────────────────────────────────

    @Test
    @DisplayName("내 동의 상태: 종류 순서(SERVICE · PRIVACY · MARKETING)로, 동의한 것은 동의한 버전 · 시각, 안 한 것은 시행 버전 · null")
    void myAgreements() {
        allCurrent();
        OffsetDateTime agreedAt = OffsetDateTime.parse("2026-11-01T00:00:00Z");
        given(termsPersistencePort.findActiveAgreements(7L)).willReturn(List.of(
                new AgreedTerms(TermsType.SERVICE, "0.9", agreedAt),
                new AgreedTerms(TermsType.PRIVACY, "1.0", agreedAt)));

        List<AgreementStatus> list = termsService.getMyAgreements(7L);

        assertThat(list).extracting(AgreementStatus::type).containsExactly(TermsType.SERVICE, TermsType.PRIVACY, TermsType.MARKETING);
        assertThat(list.get(0)).extracting(AgreementStatus::agreed, AgreementStatus::version, AgreementStatus::agreedAt)
                .containsExactly(true, "0.9", agreedAt);
        assertThat(list.get(2)).extracting(AgreementStatus::agreed, AgreementStatus::version, AgreementStatus::agreedAt)
                .containsExactly(false, "1.0", null);
    }

    @Test
    @DisplayName("같은 종류에 동의 기록이 여러 개면 가장 최근 것(먼저 온 것)을 쓴다")
    void latestAgreementWins() {
        allCurrent();
        OffsetDateTime recent = OffsetDateTime.parse("2026-11-20T00:00:00Z");
        given(termsPersistencePort.findActiveAgreements(7L)).willReturn(List.of(
                new AgreedTerms(TermsType.MARKETING, "1.0", recent),
                new AgreedTerms(TermsType.MARKETING, "1.0", OffsetDateTime.parse("2026-11-01T00:00:00Z"))));

        assertThat(termsService.getMyAgreements(7L).get(2).agreedAt()).isEqualTo(recent);
    }

    // ── 동의 · 철회 ─────────────────────────────────────────

    @Test
    @DisplayName("마케팅 동의: 동의 중이 아니면 시행 중인 버전으로 새로 기록한다")
    void agreeMarketing() {
        allCurrent();

        termsService.updateAgreement(7L, "MARKETING", true);

        verify(termsPersistencePort).saveAgreements(eq(7L), eq(List.of(3L)), any());
    }

    @Test
    @DisplayName("마케팅 동의: 이미 동의 중이면 다시 기록하지 않는다 (같은 요청을 여러 번 보내도 기록은 하나)")
    void agreeTwice() {
        allCurrent();
        given(termsPersistencePort.findActiveAgreements(7L))
                .willReturn(List.of(new AgreedTerms(TermsType.MARKETING, "1.0", OffsetDateTime.parse("2026-11-20T00:00:00Z"))));

        termsService.updateAgreement(7L, "MARKETING", true);

        verify(termsPersistencePort, never()).saveAgreements(anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("마케팅 철회: 그 종류의 동의 기록에 철회 시각을 적는다 (지우지 않음)")
    void revokeMarketing() {
        allCurrent();

        termsService.updateAgreement(7L, "MARKETING", false);

        verify(termsPersistencePort).revokeAgreements(eq(7L), eq(TermsType.MARKETING), any());
        verify(termsPersistencePort, never()).saveAgreements(anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("필수 약관은 동의 · 철회 모두 REQUIRED_TERMS_CANNOT_BE_REVOKED이고 아무것도 바꾸지 않는다")
    void requiredCannotChange() {
        allCurrent();

        for (boolean agreed : new boolean[] {true, false}) {
            assertThatThrownBy(() -> termsService.updateAgreement(7L, "PRIVACY", agreed))
                    .isInstanceOf(BusinessException.class)
                    .extracting(TermsServiceTest::errorOf)
                    .isEqualTo(TermsErrorCode.REQUIRED_TERMS_CANNOT_BE_REVOKED);
        }
        verify(termsPersistencePort, never()).saveAgreements(anyLong(), anyList(), any());
        verify(termsPersistencePort, never()).revokeAgreements(anyLong(), any(), any());
    }

    @Test
    @DisplayName("동의 변경: 모르는 종류 · 빈 값은 UNKNOWN_TERMS")
    void updateUnknown() {
        for (String type : new String[] {"LOCATION", null}) {
            assertThatThrownBy(() -> termsService.updateAgreement(7L, type, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(TermsServiceTest::errorOf)
                    .isEqualTo(TermsErrorCode.UNKNOWN_TERMS);
        }
    }
}
