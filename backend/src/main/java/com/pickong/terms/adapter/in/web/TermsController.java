package com.pickong.terms.adapter.in.web;

import com.pickong.terms.adapter.in.web.response.TermsResponse;
import com.pickong.terms.application.port.in.TermsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 약관 전문 API. 가입 전에도 봐야 하므로 로그인 없이 열려 있습니다 (SecurityConfig의 GET /terms/**). */
@RestController
@RequestMapping("/terms")
public class TermsController {

    private final TermsUseCase termsUseCase;

    public TermsController(TermsUseCase termsUseCase) {
        this.termsUseCase = termsUseCase;
    }

    /**
     * GET /api/v1/terms/{type} — 지금 시행 중인 약관 전문.
     * {@code type}은 service · privacy · marketing (대소문자 무관, 프론트 화면 경로와 같은 값). 없으면 404 TERMS_NOT_FOUND.
     */
    @GetMapping("/{type}")
    public TermsResponse get(@PathVariable String type) {
        return TermsResponse.from(termsUseCase.getCurrentTerms(type));
    }
}
