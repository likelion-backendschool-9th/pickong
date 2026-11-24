package com.pickong.terms.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
import com.pickong.terms.application.dto.PendingTerms;
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
import java.util.ArrayList;
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

    /** 1.0 시행일 */
    private static final OffsetDateTime V1 = OffsetDateTime.parse("2026-11-01T00:00:00Z");

    private static Terms terms(long id, TermsType type, boolean required) {
        return Terms.builder().id(id).type(type).version("1.0").title(type + " 1.0").required(required).effectiveAt(V1).build();
    }

    /** 이미 시행된 약관 버전들 (종류마다 가장 늦은 것이 지금 시행 중) */
    private void currentTermsAre(Terms... terms) {
        given(termsPersistencePort.findEffective(any())).willReturn(List.of(terms));
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
                new AgreedTerms(TermsType.SERVICE, "0.9", "서비스 이용약관", V1, agreedAt),
                new AgreedTerms(TermsType.PRIVACY, "1.0", "개인정보 수집 · 이용 동의", V1, agreedAt)));

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
                new AgreedTerms(TermsType.MARKETING, "1.0", "마케팅 정보 수신 동의", V1, recent),
                new AgreedTerms(TermsType.MARKETING, "1.0", "마케팅 정보 수신 동의", V1, OffsetDateTime.parse("2026-11-01T00:00:00Z"))));

        assertThat(termsService.getMyAgreements(7L).get(2).agreedAt()).isEqualTo(recent);
    }

    @Test
    @DisplayName("새 버전에서 제목이 바뀌어도, 동의한 회원에게는 동의한 버전의 버전 · 제목을 함께 보여 준다")
    void agreedVersionTitle() {
        Terms marketing11 = Terms.builder().id(4L).type(TermsType.MARKETING).version("1.1").title("마케팅 · 광고 정보 수신 동의")
                .effectiveAt(OffsetDateTime.parse("2026-11-15T00:00:00Z")).build(); // 재동의 필요 없음 → 1.0 동의가 그대로 유효
        currentTermsAre(marketing11, terms(2, TermsType.PRIVACY, true), terms(1, TermsType.SERVICE, true));
        given(termsPersistencePort.findActiveAgreements(7L)).willReturn(List.of(
                new AgreedTerms(TermsType.MARKETING, "1.0", "마케팅 정보 수신 동의", V1, OffsetDateTime.parse("2026-11-20T00:00:00Z"))));
        AgreementStatus agreed = termsService.getMyAgreements(7L).get(2);
        assertThat(agreed).extracting(AgreementStatus::version, AgreementStatus::title).containsExactly("1.0", "마케팅 정보 수신 동의");

        given(termsPersistencePort.findActiveAgreements(7L)).willReturn(List.of());
        AgreementStatus notAgreed = termsService.getMyAgreements(7L).get(2);
        assertThat(notAgreed).extracting(AgreementStatus::version, AgreementStatus::title).containsExactly("1.1", "마케팅 · 광고 정보 수신 동의");
    }

    // ── 동의 · 철회 ─────────────────────────────────────────

    @Test
    @DisplayName("마케팅 동의: 동의 중이 아니면 시행 중인 버전으로 새로 기록한다")
    void agreeMarketing() {
        allCurrent();

        termsService.updateAgreement(7L, "MARKETING", true);

        verify(termsPersistencePort).agreeIfAbsent(eq(7L), eq(3L), any());
    }

    @Test
    @DisplayName("마케팅 동의: 이미 동의 중이면 다시 기록하지 않는다 (같은 요청을 여러 번 보내도 기록은 하나)")
    void agreeTwice() {
        allCurrent();
        given(termsPersistencePort.findActiveAgreements(7L))
                .willReturn(List.of(new AgreedTerms(TermsType.MARKETING, "1.0", "마케팅 정보 수신 동의", V1, OffsetDateTime.parse("2026-11-20T00:00:00Z"))));

        termsService.updateAgreement(7L, "MARKETING", true);

        verify(termsPersistencePort, never()).agreeIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("마케팅 철회: 그 종류의 동의 기록에 철회 시각을 적는다 (지우지 않음)")
    void revokeMarketing() {
        allCurrent();

        termsService.updateAgreement(7L, "MARKETING", false);

        verify(termsPersistencePort).revokeAgreements(eq(7L), eq(TermsType.MARKETING), any());
        verify(termsPersistencePort, never()).agreeIfAbsent(anyLong(), anyLong(), any());
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
        verify(termsPersistencePort, never()).agreeIfAbsent(anyLong(), anyLong(), any());
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

    // ── 새 버전 재동의 ──────────────────────────────────────

    /** 1.1 시행일 (테스트 '지금'인 2026-11-23보다 앞) */
    private static final OffsetDateTime V11 = OffsetDateTime.parse("2026-11-15T00:00:00Z");

    private static Terms v11(long id, TermsType type, boolean required, boolean requiresReconsent) {
        return Terms.builder().id(id).type(type).version("1.1").title(type + " 1.1").required(required)
                .effectiveAt(V11).requiresReconsent(requiresReconsent).build();
    }

    private static AgreedTerms agreedTo10(TermsType type) {
        return new AgreedTerms(type, "1.0", type + " 1.0", V1, OffsetDateTime.parse("2026-11-02T00:00:00Z"));
    }

    /** 1.0 세 개 + 개인정보 1.1(재동의 필요) 시행. 회원은 1.0 세 개에 모두 동의한 상태 */
    private void privacyReconsentRequired(boolean marketingAgreed) {
        currentTermsAre(terms(1, TermsType.SERVICE, true), terms(2, TermsType.PRIVACY, true), terms(3, TermsType.MARKETING, false),
                v11(12, TermsType.PRIVACY, true, true));
        List<AgreedTerms> agreed = new ArrayList<>(List.of(agreedTo10(TermsType.SERVICE), agreedTo10(TermsType.PRIVACY)));
        if (marketingAgreed) {
            agreed.add(agreedTo10(TermsType.MARKETING));
        }
        given(termsPersistencePort.findActiveAgreements(7L)).willReturn(agreed);
    }

    @Test
    @DisplayName("재동의 필요 없는 새 버전: 예전 동의가 그대로 유효하고 동의 받을 약관이 없다")
    void newVersionWithoutReconsent() {
        currentTermsAre(terms(1, TermsType.SERVICE, true), terms(2, TermsType.PRIVACY, true), v11(12, TermsType.PRIVACY, true, false));
        given(termsPersistencePort.findActiveAgreements(7L)).willReturn(List.of(agreedTo10(TermsType.SERVICE), agreedTo10(TermsType.PRIVACY)));

        assertThat(termsService.getPendingTerms(7L)).isEmpty();
        assertThat(termsService.hasPendingRequiredTerms(7L)).isFalse();
        assertThat(termsService.getMyAgreements(7L).get(1)).extracting(AgreementStatus::agreed, AgreementStatus::version)
                .containsExactly(true, "1.0");
    }

    @Test
    @DisplayName("필수 약관 재동의 필요: 예전 동의는 '동의하지 않음'(시행 버전)으로 보이고, 동의 받을 필수 약관으로 나온다")
    void requiredReconsentPending() {
        privacyReconsentRequired(false);

        assertThat(termsService.getMyAgreements(7L).get(1)).extracting(AgreementStatus::agreed, AgreementStatus::version)
                .containsExactly(false, "1.1");
        assertThat(termsService.getPendingTerms(7L)).containsExactly(new PendingTerms(TermsType.PRIVACY, "PRIVACY 1.1", true, "1.1"));
        assertThat(termsService.hasPendingRequiredTerms(7L)).isTrue();
    }

    @Test
    @DisplayName("선택 약관 재동의 필요: 예전에 동의한 회원에게만 다시 묻고(선택), 서비스는 막지 않는다")
    void optionalReconsentOnlyForPreviouslyAgreed() {
        currentTermsAre(terms(1, TermsType.SERVICE, true), terms(3, TermsType.MARKETING, false), v11(13, TermsType.MARKETING, false, true));
        given(termsPersistencePort.findActiveAgreements(7L)).willReturn(List.of(agreedTo10(TermsType.SERVICE), agreedTo10(TermsType.MARKETING)));

        assertThat(termsService.getPendingTerms(7L)).extracting(PendingTerms::type, PendingTerms::required)
                .containsExactly(tuple(TermsType.MARKETING, false));
        assertThat(termsService.hasPendingRequiredTerms(7L)).isFalse();

        given(termsPersistencePort.findActiveAgreements(8L)).willReturn(List.of(agreedTo10(TermsType.SERVICE)));
        assertThat(termsService.getPendingTerms(8L)).as("마케팅에 동의한 적 없는 회원").isEmpty();
    }

    @Test
    @DisplayName("재동의 필요 버전이 없으면, 동의 기록이 없는 회원도 막지 않는다 (지금 1.0만 있는 상태와 같음)")
    void noReconsentVersionNoPending() {
        allCurrent();

        assertThat(termsService.getPendingTerms(7L)).isEmpty();
        assertThat(termsService.hasPendingRequiredTerms(7L)).isFalse();
    }

    @Test
    @DisplayName("재동의: 동의 받을 필수 약관이 빠지면 REQUIRED_TERMS_NOT_AGREED이고 아무것도 기록하지 않는다")
    void reconsentMissingRequired() {
        privacyReconsentRequired(false);

        assertThatThrownBy(() -> termsService.agreeToPendingTerms(7L, Set.of("SERVICE")))
                .isInstanceOf(BusinessException.class)
                .extracting(TermsServiceTest::errorOf)
                .isEqualTo(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
        verify(termsPersistencePort, never()).agreeIfAbsent(anyLong(), anyLong(), any());
        verify(termsPersistencePort, never()).revokeAgreements(anyLong(), any(), any());
    }

    @Test
    @DisplayName("재동의: 유효한 동의가 없는 것만 시행 버전으로 기록한다 (이미 유효한 서비스 약관은 다시 기록하지 않음, 대소문자 무관)")
    void reconsentRecordsOnlyMissing() {
        privacyReconsentRequired(false);

        termsService.agreeToPendingTerms(7L, Set.of("service", "PRIVACY"));

        verify(termsPersistencePort).agreeIfAbsent(eq(7L), eq(12L), any());
        verify(termsPersistencePort, never()).agreeIfAbsent(eq(7L), eq(1L), any());
    }

    @Test
    @DisplayName("재동의: 다시 물은 선택 약관을 보내지 않으면 예전 동의를 철회해 다음 로그인부터 묻지 않는다")
    void reconsentDeclineOptionalRevokes() {
        currentTermsAre(terms(1, TermsType.SERVICE, true), terms(3, TermsType.MARKETING, false), v11(13, TermsType.MARKETING, false, true));
        given(termsPersistencePort.findActiveAgreements(7L)).willReturn(List.of(agreedTo10(TermsType.SERVICE), agreedTo10(TermsType.MARKETING)));

        termsService.agreeToPendingTerms(7L, Set.of());

        verify(termsPersistencePort).revokeAgreements(eq(7L), eq(TermsType.MARKETING), any());
        verify(termsPersistencePort, never()).agreeIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("재동의: 모르는 종류는 UNKNOWN_TERMS")
    void reconsentUnknownType() {
        assertThatThrownBy(() -> termsService.agreeToPendingTerms(7L, Set.of("LOCATION")))
                .isInstanceOf(BusinessException.class)
                .extracting(TermsServiceTest::errorOf)
                .isEqualTo(TermsErrorCode.UNKNOWN_TERMS);
    }

    @Test
    @DisplayName("설정에서 마케팅 동의: 재동의 필요 버전이 시행된 뒤에는 예전 동의가 있어도 시행 버전으로 새로 기록한다 (#89 리뷰 4번)")
    void settingsAgreeAfterReconsentVersion() {
        currentTermsAre(terms(1, TermsType.SERVICE, true), terms(3, TermsType.MARKETING, false), v11(13, TermsType.MARKETING, false, true));
        given(termsPersistencePort.findActiveAgreements(7L)).willReturn(List.of(agreedTo10(TermsType.MARKETING)));

        termsService.updateAgreement(7L, "MARKETING", true);

        verify(termsPersistencePort).agreeIfAbsent(eq(7L), eq(13L), any());
    }
}
