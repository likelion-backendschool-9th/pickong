package com.pickong.auth.adapter.in.web;

import com.pickong.auth.adapter.in.security.OAuth2LinkRequests;
import com.pickong.auth.adapter.in.web.response.LinkStartResponse;
import com.pickong.auth.adapter.in.web.response.LoginMethodResponse;
import com.pickong.auth.application.port.in.SocialAccountUseCase;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 수단 관리 API (설정 > 로그인 수단 관리). 모두 로그인이 필요하고, POST · DELETE는 CSRF 헤더(X-XSRF-TOKEN)가 필요합니다.
 * 경로의 {provider}는 소문자 kakao · google (프론트 providerSlug와 같음).
 */
@RestController
@RequestMapping("/me/social-accounts")
public class SocialAccountController {

    private final SocialAccountUseCase socialAccountUseCase;

    public SocialAccountController(SocialAccountUseCase socialAccountUseCase) {
        this.socialAccountUseCase = socialAccountUseCase;
    }

    /** GET /api/v1/me/social-accounts — 연결된 로그인 수단 (연결 순서대로, 가입 수단은 canUnlink=false) */
    @GetMapping
    public List<LoginMethodResponse> list(@CurrentUser LoginUser user) {
        return socialAccountUseCase.list(user.userId()).stream().map(LoginMethodResponse::from).toList();
    }

    /**
     * POST /api/v1/me/social-accounts/{provider} — 연결 시작.
     * <ul>
     *   <li>200 {@code {"authorizationUrl":"/api/v1/auth/oauth2/authorization/google?link=..."}} → 프론트가 이 주소로 이동</li>
     *   <li>409 PROVIDER_ALREADY_LINKED — 이 제공자가 이미 연결돼 있음</li>
     * </ul>
     * POST로 받는 이유: 다른 사이트가 링크 하나로 연결을 시작시키지 못하게(CSRF 헤더 필요) 하기 위해서입니다.
     */
    @PostMapping("/{provider}")
    public LinkStartResponse startLink(@CurrentUser LoginUser user, @PathVariable String provider) {
        SocialProvider socialProvider = parse(provider);
        String linkToken = socialAccountUseCase.startLink(user.userId(), socialProvider);
        return new LinkStartResponse(OAuth2LinkRequests.authorizationUrl(socialProvider, linkToken));
    }

    /**
     * DELETE /api/v1/me/social-accounts/{provider} — 연결 해제 → 204.
     * <ul>
     *   <li>404 SOCIAL_ACCOUNT_NOT_LINKED — 연결돼 있지 않음</li>
     *   <li>409 SIGNUP_METHOD_CANNOT_BE_UNLINKED — 가입할 때 쓴 수단</li>
     * </ul>
     */
    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> unlink(@CurrentUser LoginUser user, @PathVariable String provider) {
        socialAccountUseCase.unlink(user.userId(), parse(provider));
        return ResponseEntity.noContent().build();
    }

    /** kakao · google만 받습니다. 그 밖의 값은 400 INVALID_INPUT */
    private static SocialProvider parse(String slug) {
        return SocialProvider.fromSlug(slug).orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT));
    }
}
