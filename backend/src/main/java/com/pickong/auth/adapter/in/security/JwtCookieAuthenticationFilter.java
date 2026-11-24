package com.pickong.auth.adapter.in.security;

import com.pickong.common.security.AccessTokenVerifier;
import com.pickong.common.security.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 쿠키(pickong_at)의 Access Token을 검증해 로그인 회원을 SecurityContext에 넣는 필터.
 * <p>
 * <b>요청 하나가 들어왔을 때의 흐름</b>
 * <ol>
 *   <li>요청 쿠키에서 pickong_at 값을 찾는다 ({@link #readAccessToken})</li>
 *   <li>있으면 서명 · 만료를 검증한다 ({@link AccessTokenVerifier#verify})</li>
 *   <li>검증에 성공하면 "이 요청은 회원 N번이 보냈다"고 기록한다 ({@link #authenticate})</li>
 *   <li>성공하든 실패하든 다음 필터로 넘긴다</li>
 * </ol>
 * 토큰이 없거나 만료 · 위조되었으면 <b>여기서 거절하지 않고 그냥 통과</b>시킵니다(비로그인으로 처리).
 * 로그인이 필요한 경로는 뒤의 인가 단계(SecurityConfig)가 401로 막고, 공개 경로(재발급 /auth/refresh, 공개 피드 등)는
 * 만료된 쿠키가 남아 있어도 정상 동작해야 하기 때문입니다.
 * (Spring의 Resource Server 필터는 잘못된 토큰이 오면 공개 경로에서도 401을 내서 쓰지 않았습니다.)
 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    /** 로그인한 회원에게 주는 권한. 관리자는 별도 계정이라 회원은 모두 ROLE_USER 하나입니다. */
    static final List<SimpleGrantedAuthority> MEMBER_AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final AccessTokenVerifier verifier;

    /** SecurityContext(현재 요청의 로그인 정보)를 꺼내고 넣는 도구. Spring Security 권장 방식대로 정적 호출 대신 필드로 둡니다. */
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    /**
     * @param verifier 토큰 검증기. 실제 구현은 JwtTokenAdapter이고, 테스트에서는 가짜로 바꿔 끼울 수 있게 인터페이스로 받습니다.
     */
    public JwtCookieAuthenticationFilter(AccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    /**
     * 모든 요청마다 한 번씩 실행되는 필터 본문.
     * <p>
     * 쿠키에서 토큰을 꺼내 → 검증하고 → 성공했을 때만 로그인 정보를 넣은 뒤,
     * 결과와 관계없이 항상 다음 필터로 요청을 넘깁니다. (거절은 이 필터의 일이 아님)
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        readAccessToken(request)          // 1. 쿠키에서 토큰 문자열 찾기 (없으면 빈 값)
                .flatMap(verifier::verify) // 2. 서명 · 만료 검증 (실패하면 빈 값)
                .ifPresent(this::authenticate); // 3. 성공했을 때만 로그인 처리
        chain.doFilter(request, response);  // 4. 성공 · 실패와 관계없이 다음 단계로
    }

    /**
     * 요청 쿠키 중 pickong_at의 값을 찾습니다.
     *
     * @return 토큰 문자열. 쿠키가 아예 없거나, pickong_at이 없거나, 값이 비어 있으면 빈 Optional
     */
    private Optional<String> readAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            // 쿠키가 하나도 없는 요청이면 null이 옵니다 (빈 배열이 아님)
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (AuthCookies.ACCESS_TOKEN.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * 검증된 회원을 "이 요청의 로그인 사용자"로 기록합니다.
     * <p>
     * 여기서 넣은 정보 덕분에 뒤에서
     * <ul>
     *   <li>SecurityConfig의 {@code authenticated()} 검사가 통과하고</li>
     *   <li>컨트롤러의 {@code @CurrentUser LoginUser}에 이 회원이 들어갑니다</li>
     * </ul>
     * 세션을 쓰지 않으므로 이 정보는 요청이 끝나면 사라지고, 다음 요청에서 다시 쿠키로 확인합니다.
     */
    private void authenticate(LoginUser loginUser) {
        SecurityContext context = contextHolder.createEmptyContext();
        // principal(누구인가) = LoginUser, credentials(비밀번호 등) = 없음, authorities(권한) = ROLE_USER
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(loginUser, null, MEMBER_AUTHORITIES));
        contextHolder.setContext(context);
    }
}
