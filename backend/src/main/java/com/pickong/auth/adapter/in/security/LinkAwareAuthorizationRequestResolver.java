package com.pickong.auth.adapter.in.security;

import com.pickong.auth.application.port.in.SocialAccountUseCase;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.common.security.AccessTokenVerifier;
import com.pickong.user.application.port.in.UserAccountUseCase;
import com.pickong.common.security.LoginUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

/**
 * OAuth 시작 주소(/api/v1/auth/oauth2/authorization/{kakao|google})에서 제공자 동의 화면으로 보낼 요청을 만듭니다.
 * <ul>
 *   <li>{@code ?link=}가 없으면 → 일반 로그인. Spring 기본 동작에, Google만 {@code prompt=select_account}를 더합니다.
 *       (Google은 한 번 고른 계정으로 선택 화면 없이 바로 통과시켜서, 그 이메일이 이미 다른 수단으로 가입돼 로그인에 실패하면
 *       다른 Google 계정으로 다시 시도할 방법이 없기 때문입니다. 카카오는 가입된 수단으로 그대로 로그인하면 되므로 두지 않습니다)</li>
 *   <li>{@code ?link=}가 있으면 → 계정 연결. 아래 두 가지가 모두 맞아야 진행하고, 아니면 {@link LinkStartRejectedException}</li>
 * </ul>
 * 연결 확인:
 * <ol>
 *   <li>연결 토큰이 유효하고 이 제공자용이다 (5분, POST /me/social-accounts/{provider}에서 발급)</li>
 *   <li>로그인 쿠키(Access Token)의 회원이 토큰의 회원과 같다</li>
 * </ol>
 * 2번 덕분에 연결 주소가 새어 나가도 다른 사람이 자기 브라우저에서 쓸 수 없습니다.
 * 통과하면 회원 id를 시작 정보에 넣어 서명된 state 쿠키에 저장하고, 제공자에 {@code prompt=select_account}를 보내
 * 어느 계정을 연결할지 사용자가 직접 고르게 합니다. (브라우저에 다른 사람 계정이 로그인돼 있어도 모르고 연결하지 않도록)
 * <p>
 * Access Token은 보안 필터 체인에서 이 단계보다 뒤에 검증되므로(OAuth 시작 필터가 앞에 있음) 여기서 쿠키를 직접 확인합니다.
 */
@Component
public class LinkAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver loginResolver;
    private final DefaultOAuth2AuthorizationRequestResolver linkResolver;
    private final AccessTokenVerifier accessTokenVerifier;
    private final SocialAccountUseCase socialAccountUseCase;
    private final UserAccountUseCase accounts;

    /**
     * 기본 resolver를 두 개 만듭니다. 연결용은 모든 제공자에, 로그인용은 Google에만 계정 선택 화면 파라미터를 더합니다.
     * (기본 resolver 안에서 붙여야 제공자로 가는 주소에 반영됩니다)
     */
    public LinkAwareAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrations,
            AccessTokenVerifier accessTokenVerifier, SocialAccountUseCase socialAccountUseCase, UserAccountUseCase accounts) {
        this.loginResolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, SecurityConfig.OAUTH2_AUTHORIZATION_BASE_URI);
        this.loginResolver.setAuthorizationRequestCustomizer(LinkAwareAuthorizationRequestResolver::selectAccountForGoogle);
        this.linkResolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, SecurityConfig.OAUTH2_AUTHORIZATION_BASE_URI);
        this.linkResolver.setAuthorizationRequestCustomizer(LinkAwareAuthorizationRequestResolver::selectAccount);
        this.accessTokenVerifier = accessTokenVerifier;
        this.socialAccountUseCase = socialAccountUseCase;
        this.accounts = accounts;
    }

    /** 제공자에게 계정 선택 화면을 띄우라고 요청합니다. (Google · 카카오 모두 같은 이름의 값을 받습니다) */
    private static void selectAccount(OAuth2AuthorizationRequest.Builder builder) {
        builder.additionalParameters(params -> params.put("prompt", "select_account"));
    }

    /**
     * 로그인용: Google일 때만 계정 선택 화면을 띄웁니다.
     * 기본 resolver가 이 단계 전에 시작 정보에 제공자 이름(registration_id)을 넣어 두므로 그걸 보고 나눕니다.
     */
    private static void selectAccountForGoogle(OAuth2AuthorizationRequest.Builder builder) {
        builder.attributes(attributes -> {
            if (SocialProvider.GOOGLE.slug().equals(attributes.get(OAuth2ParameterNames.REGISTRATION_ID))) {
                selectAccount(builder);
            }
        });
    }

    /** 요청 주소에서 제공자를 읽습니다. 시작 주소가 아니면 null(다음 필터로 넘어감) */
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String linkToken = request.getParameter(OAuth2LinkRequests.LINK_PARAM);
        if (linkToken == null) {
            return loginResolver.resolve(request);
        }
        return forLink(request, linkResolver.resolve(request), linkToken);
    }

    /** 제공자를 직접 받는 경우(Spring 내부에서 사용) */
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        String linkToken = request.getParameter(OAuth2LinkRequests.LINK_PARAM);
        if (linkToken == null) {
            return loginResolver.resolve(request, clientRegistrationId);
        }
        return forLink(request, linkResolver.resolve(request, clientRegistrationId), linkToken);
    }

    /** 연결 토큰과 로그인 쿠키를 확인하고, 연결할 회원 id를 시작 정보에 넣습니다. */
    private OAuth2AuthorizationRequest forLink(HttpServletRequest request, OAuth2AuthorizationRequest base, String linkToken) {
        if (base == null) {
            return null;
        }
        String registrationId = base.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
        SocialProvider provider = SocialProvider.fromSlug(registrationId)
                .orElseThrow(() -> new LinkStartRejectedException("모르는 제공자: " + registrationId));
        Long tokenUserId = socialAccountUseCase.verifyLinkToken(linkToken, provider)
                .orElseThrow(() -> new LinkStartRejectedException("연결 토큰이 없거나 만료 · 위조됨"));
        Long loginUserId = currentUserId(request);
        if (!tokenUserId.equals(loginUserId)) {
            throw new LinkStartRejectedException("로그인 쿠키가 없거나 연결 토큰의 회원과 다름");
        }
        return OAuth2AuthorizationRequest.from(base)
                .attributes(attributes -> attributes.put(OAuth2LinkRequests.LINK_USER_ATTRIBUTE, tokenUserId.toString()))
                .build();
    }

    /** 로그인 쿠키의 회원 id. 없거나 만료면 null */
    private Long currentUserId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookies.ACCESS_TOKEN.equals(cookie.getName())) {
                return accessTokenVerifier.verify(cookie.getValue()).filter(user -> accounts.canLogin(user.userId()))
                        .map(LoginUser::userId).orElse(null);
            }
        }
        return null;
    }
}
