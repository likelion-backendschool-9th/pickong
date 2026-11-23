package com.pickong.auth.adapter.in.security;

import com.pickong.auth.application.dto.SocialLoginResult;
import com.pickong.auth.application.dto.SocialProfile;
import com.pickong.auth.application.port.in.LoginUseCase;
import com.pickong.auth.application.port.in.SocialAccountUseCase;
import com.pickong.auth.application.port.in.TokenUseCase;
import com.pickong.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 카카오 · Google 인증이 성공한 직후(콜백) 호출됩니다. 소셜 계정으로 찜콩 회원을 판단하고, 쿠키를 쓰고, 프론트 화면으로 보냅니다.
 * 같은 콜백을 로그인한 회원의 <b>계정 연결</b>도 함께 씁니다({@link OAuth2LinkRequests}).
 * <p>
 * 판단은 {@link LoginUseCase} · {@link SocialAccountUseCase}, 세션 발급은 {@link TokenUseCase}가 하고,
 * 이 클래스는 그것들을 이어 붙여 HTTP 응답으로 바꾸기만 합니다.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final OAuth2ProfileMapper profileMapper;
    private final LoginUseCase loginUseCase;
    private final SocialAccountUseCase socialAccountUseCase;
    private final TokenUseCase tokenUseCase;
    private final TokenCookieWriter cookieWriter;
    private final FrontendProperties frontend;

    public OAuth2LoginSuccessHandler(OAuth2ProfileMapper profileMapper, LoginUseCase loginUseCase,
            SocialAccountUseCase socialAccountUseCase, TokenUseCase tokenUseCase, TokenCookieWriter cookieWriter,
            FrontendProperties frontend) {
        this.profileMapper = profileMapper;
        this.loginUseCase = loginUseCase;
        this.socialAccountUseCase = socialAccountUseCase;
        this.tokenUseCase = tokenUseCase;
        this.cookieWriter = cookieWriter;
        this.frontend = frontend;
    }

    /**
     * 소셜 계정 정보를 꺼낸 뒤, 계정 연결로 시작한 것이면 {@link #link}, 아니면 {@link #login}으로 처리합니다.
     * 예상하지 못한 오류(응답 형식이 다름 등)는 OAUTH_FAILED로 안내합니다. (로그인 → /login, 연결 → /settings/login-methods)
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        Optional<Long> linkUserId = OAuth2LinkRequests.linkUserId(request);
        String target;
        try {
            OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
            SocialProfile profile = profileMapper.map(oauth.getAuthorizedClientRegistrationId(), oauth.getPrincipal());
            target = linkUserId.isPresent() ? link(linkUserId.get(), profile) : login(profile, response);
        } catch (RuntimeException e) {
            log.warn("소셜 {} 처리 실패: {}", linkUserId.isPresent() ? "계정 연결" : "로그인", e.toString());
            target = linkUserId.isPresent() ? OAuth2LinkRequests.failurePath("OAUTH_FAILED") : "/login?error=OAUTH_FAILED";
        }
        response.sendRedirect(frontend.url(target));
    }

    /**
     * 계정 연결. 로그인 세션 쿠키는 건드리지 않습니다(이미 로그인한 상태 그대로).
     * <ul>
     *   <li>성공 → /settings/login-methods?linked=kakao</li>
     *   <li>규칙 위반 → /settings/login-methods?error=오류 코드 (SOCIAL_ACCOUNT_ALREADY_LINKED · PROVIDER_ALREADY_LINKED ·
     *       EMAIL_ALREADY_REGISTERED · ACCOUNT_UNAVAILABLE)</li>
     * </ul>
     */
    private String link(Long userId, SocialProfile profile) {
        try {
            socialAccountUseCase.link(userId, profile);
            return OAuth2LinkRequests.LOGIN_METHODS_PATH + "?linked=" + profile.provider().slug();
        } catch (BusinessException e) {
            log.info("계정 연결 거절: {} ({})", e.getErrorCode().getCode(), profile);
            return OAuth2LinkRequests.failurePath(e.getErrorCode().getCode());
        }
    }

    /**
     * 로그인. 결과별 처리:
     * <ul>
     *   <li>기존 회원 → 세션 쿠키(Access · Refresh) 발급, 남아 있던 가입 쿠키 삭제 → /feed</li>
     *   <li>신규 회원 → 임시 가입 쿠키 발급, 남아 있던 다른 계정의 세션 쿠키 삭제 → /signup</li>
     *   <li>정지 · 탈퇴 → /login?error=ACCOUNT_UNAVAILABLE</li>
     *   <li>이메일이 이미 다른 계정에 있음 → /login?error=EMAIL_ALREADY_REGISTERED&provider=그 계정의 제공자</li>
     * </ul>
     */
    private String login(SocialProfile profile, HttpServletResponse response) {
        return switch (loginUseCase.loginWithSocial(profile)) {
            case SocialLoginResult.ExistingMember member -> {
                cookieWriter.clearSignup(response);
                cookieWriter.write(response, tokenUseCase.issue(member.userId()));
                yield "/feed";
            }
            case SocialLoginResult.NewMember member -> {
                // 다른 계정으로 로그인돼 있던 쿠키가 남아 있으면 온보딩 중에 섞이지 않게 지웁니다.
                cookieWriter.clear(response);
                cookieWriter.writeSignup(response, member.signupToken());
                yield "/signup";
            }
            case SocialLoginResult.Unavailable unavailable -> "/login?error=ACCOUNT_UNAVAILABLE";
            case SocialLoginResult.EmailAlreadyRegistered taken ->
                    "/login?error=EMAIL_ALREADY_REGISTERED&provider=" + taken.existingProvider().slug();
        };
    }
}
