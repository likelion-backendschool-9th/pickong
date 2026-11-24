package com.pickong.terms.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.terms.adapter.in.web.request.AgreePendingTermsRequest;
import com.pickong.terms.adapter.in.web.request.UpdateAgreementRequest;
import com.pickong.terms.adapter.in.web.response.AgreementResponse;
import com.pickong.terms.adapter.in.web.response.PendingTermsResponse;
import com.pickong.terms.application.dto.AgreementStatus;
import com.pickong.terms.application.port.in.TermsUseCase;
import java.util.HashSet;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 약관 동의 관리 API (설정 > 약관 및 동의 관리 · 재동의 화면). 로그인이 필요하고, PATCH · POST는 CSRF 헤더가 필요합니다.
 * 동의 받을 필수 약관이 남아 있어도 이 경로들은 재동의 필터가 막지 않습니다(동의하러 와야 하므로).
 */
@RestController
@RequestMapping("/me/agreements")
public class MyAgreementsController {

    private final TermsUseCase termsUseCase;

    public MyAgreementsController(TermsUseCase termsUseCase) {
        this.termsUseCase = termsUseCase;
    }

    /** GET /api/v1/me/agreements — 시행 중인 약관마다 내 동의 상태 (SERVICE · PRIVACY · MARKETING 순서) */
    @GetMapping
    public List<AgreementResponse> list(@CurrentUser LoginUser user) {
        return toResponses(termsUseCase.getMyAgreements(user.userId()));
    }

    /**
     * PATCH /api/v1/me/agreements — 선택 약관 동의 · 철회. 바뀐 동의 상태 전체를 돌려줍니다.
     * <ul>
     *   <li>400 INVALID_INPUT — agreed가 없거나 true · false가 아님 (숫자 0 · 1, 문자열 "true"도 거절)</li>
     *   <li>400 UNKNOWN_TERMS — 모르는 종류</li>
     *   <li>400 REQUIRED_TERMS_CANNOT_BE_REVOKED — 필수 약관(해지는 탈퇴로만)</li>
     * </ul>
     */
    @PatchMapping
    public List<AgreementResponse> update(@CurrentUser LoginUser user, @RequestBody UpdateAgreementRequest request) {
        Boolean agreed = request.agreedValue();
        if (agreed == null) { // 빠졌거나 boolean이 아님 (0 · 1 · "true"도 거절)
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return toResponses(termsUseCase.updateAgreement(user.userId(), request.type(), agreed));
    }

    /** GET /api/v1/me/agreements/pending — 지금 동의를 받아야 하는 약관 (재동의 화면). 없으면 빈 배열 */
    @GetMapping("/pending")
    public List<PendingTermsResponse> pending(@CurrentUser LoginUser user) {
        return termsUseCase.getPendingTerms(user.userId()).stream().map(PendingTermsResponse::from).toList();
    }

    /**
     * POST /api/v1/me/agreements — 재동의 화면의 동의를 한 번에 기록하고, 바뀐 동의 상태 전체를 돌려줍니다.
     * <ul>
     *   <li>400 REQUIRED_TERMS_NOT_AGREED — 동의 받을 필수 약관이 빠짐 (아무것도 저장하지 않음)</li>
     *   <li>400 UNKNOWN_TERMS — 모르는 종류</li>
     * </ul>
     */
    @PostMapping
    public List<AgreementResponse> agree(@CurrentUser LoginUser user, @RequestBody AgreePendingTermsRequest request) {
        List<String> agreed = request.agreedTerms() == null ? List.of() : request.agreedTerms();
        return toResponses(termsUseCase.agreeToPendingTerms(user.userId(), new HashSet<>(agreed)));
    }

    private static List<AgreementResponse> toResponses(List<AgreementStatus> statuses) {
        return statuses.stream().map(AgreementResponse::from).toList();
    }
}
