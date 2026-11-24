package com.pickong.auth.adapter.in.security;

import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.common.security.LoginUser;
import com.pickong.terms.application.port.in.TermsUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 재동의가 필요한 필수 약관에 동의하지 않은 회원의 요청을 403 TERMS_RECONSENT_REQUIRED로 막는 필터.
 * <p>
 * 화면에서만 막으면 API를 직접 부르는 것으로 동의 없이 계속 쓸 수 있어서 서버에서도 막습니다.
 * 동의하러 가는 데 필요한 요청(약관 조회 · 동의, 내 정보, 로그아웃 · 재발급, 탈퇴)은 열어 둡니다({@code allowed}).
 * <p>
 * 인가 단계(AuthorizationFilter) 뒤에 두어, 비로그인 요청은 원래대로 401 · 공개 처리되고 로그인한 회원만 검사합니다.
 * 확인은 요청마다 DB로 합니다(시행된 약관 · 내 동의 두 번 조회). 토큰에 넣으면 새 버전 시행 뒤 토큰이 만료될 때까지 막지 못하고,
 * 동의한 뒤 토큰을 다시 발급해야 하기 때문입니다.
 */
public class TermsConsentFilter extends OncePerRequestFilter {

    private final TermsUseCase termsUseCase;
    private final List<RequestMatcher> allowed;
    private final ErrorResponseWriter errorWriter;

    /** JwtCookieAuthenticationFilter와 같은 방식으로 현재 요청의 로그인 정보를 꺼냅니다. */
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    /**
     * @param allowed 동의 전에도 열어 둘 요청 (SecurityConfig가 /api/v1 기준 matcher로 만들어 넘김)
     */
    public TermsConsentFilter(TermsUseCase termsUseCase, List<RequestMatcher> allowed, ErrorResponseWriter errorWriter) {
        this.termsUseCase = termsUseCase;
        this.allowed = allowed;
        this.errorWriter = errorWriter;
    }

    /** 로그인한 회원 + 열어 둔 요청이 아님 + 동의 받을 필수 약관이 있음 → 403. 그 밖에는 그대로 다음 단계로 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user
                && allowed.stream().noneMatch(matcher -> matcher.matches(request))
                && termsUseCase.hasPendingRequiredTerms(user.userId())) {
            errorWriter.write(response, AuthErrorCode.TERMS_RECONSENT_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }
}
