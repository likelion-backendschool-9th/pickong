package com.pickong.terms.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.terms.adapter.in.web.request.UpdateAgreementRequest;
import com.pickong.terms.adapter.in.web.response.AgreementResponse;
import com.pickong.terms.application.dto.AgreementStatus;
import com.pickong.terms.application.port.in.TermsUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 약관 동의 관리 API (설정 > 약관 및 동의 관리). 로그인이 필요하고, PATCH는 CSRF 헤더가 필요합니다. */
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
     *   <li>400 INVALID_INPUT — agreed가 없음</li>
     *   <li>400 UNKNOWN_TERMS — 모르는 종류</li>
     *   <li>400 REQUIRED_TERMS_CANNOT_BE_REVOKED — 필수 약관(해지는 탈퇴로만)</li>
     * </ul>
     */
    @PatchMapping
    public List<AgreementResponse> update(@CurrentUser LoginUser user, @RequestBody UpdateAgreementRequest request) {
        if (request.agreed() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return toResponses(termsUseCase.updateAgreement(user.userId(), request.type(), request.agreed()));
    }

    private static List<AgreementResponse> toResponses(List<AgreementStatus> statuses) {
        return statuses.stream().map(AgreementResponse::from).toList();
    }
}
