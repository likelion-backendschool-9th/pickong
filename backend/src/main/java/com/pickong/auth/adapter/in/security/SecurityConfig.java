package com.pickong.auth.adapter.in.security;

import com.pickong.auth.adapter.out.external.jwt.JwtProperties;
import com.pickong.common.security.AccessTokenVerifier;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * 회원 API 보안 설정.
 * <ul>
 *   <li>세션을 쓰지 않습니다(STATELESS). 인증은 매 요청 쿠키의 Access Token으로 합니다.</li>
 *   <li>기본은 로그인 필요. 공개 경로만 아래에 명시합니다(새 API가 실수로 공개되지 않도록).</li>
 *   <li>CSRF: 쿠키는 SameSite=Lax로 내려주고, 상태를 바꾸는 요청(POST · PUT · PATCH · DELETE)은
 *       XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내야 합니다(Spring의 SPA용 설정).</li>
 *   <li>401 · 403은 공통 오류 형식(JSON)으로 응답합니다.</li>
 * </ul>
 * <b>경로는 /api/v1을 빼고 적습니다.</b> DispatcherServlet이 /api/v1에 매핑되어 있으면 Spring Boot가
 * 보안 경로 패턴의 기준 경로(basePath)를 /api/v1로 자동 설정하기 때문입니다.
 * (ServletWebSecurityAutoConfiguration: "/auth/**"는 실제로 "/api/v1/auth/**"에 걸림)
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, FrontendProperties.class})
public class SecurityConfig {

    /**
     * 로그인 없이 열어 두는 경로. /api/v1을 빼고 적습니다(Spring Boot가 basePath로 붙임).
     * 여기에 없는 경로는 모두 로그인이 필요합니다.
     */
    private static final String[] PUBLIC_PATHS = {
            "/auth/**",                              // 로그인 · 가입 · 재발급 · 로그아웃 (각자 쿠키로 따로 검증)
            "/actuator/health", "/actuator/health/**",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
    };

    /**
     * 모든 API 요청이 통과하는 보안 필터 체인을 만듭니다. 위에서부터 순서대로 읽으면 됩니다.
     * <ol>
     *   <li>CSRF: 상태를 바꾸는 요청에 XSRF 토큰 헤더가 있는지 확인</li>
     *   <li>세션 · 로그인 페이지 같은 기본 기능 끄기 (API 서버라 필요 없음)</li>
     *   <li>쿠키 토큰 검증 필터 끼워 넣기 (로그인 회원 채우기)</li>
     *   <li>401 · 403이 나면 공통 오류 JSON으로 응답</li>
     *   <li>경로별로 로그인이 필요한지 결정</li>
     * </ol>
     * 파라미터는 Spring이 빈으로 넣어 줍니다. (verifier = JwtTokenAdapter)
     */
    /**
     * 소셜 로그인 시작 주소의 앞부분: /api/v1/auth/oauth2/authorization/{kakao|google}
     * <p>
     * <b>OAuth2 경로 두 개(시작 · 콜백)는 /api/v1을 포함한 전체 경로로 적습니다.</b> 아래 PUBLIC_PATHS 같은 인가 규칙과 달리,
     * OAuth2 로그인 필터의 matcher에는 Spring Boot의 basePath(/api/v1)가 붙지 않기 때문입니다.
     * (실제 서버 TRACE 로그에서 확인: 콜백 matcher가 "PathPattern [/auth/oauth2/callback/*]"로 만들어져 매칭 실패)
     */
    static final String OAUTH2_AUTHORIZATION_BASE_URI = "/api/v1/auth/oauth2/authorization";

    /**
     * 소셜 로그인 콜백 주소: /api/v1/auth/oauth2/callback/{kakao|google} (위와 같은 이유로 전체 경로)
     * 카카오 · Google에 등록하는 Redirect URI는 application.yml에서 FRONTEND_BASE_URL로 만듭니다.
     */
    static final String OAUTH2_CALLBACK_URI = "/api/v1/auth/oauth2/callback/*";

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, AccessTokenVerifier verifier,
            RestAuthenticationEntryPoint authenticationEntryPoint, RestAccessDeniedHandler accessDeniedHandler,
            SignedCookieAuthorizationRequestRepository authorizationRequestRepository,
            LinkAwareAuthorizationRequestResolver authorizationRequestResolver,
            OAuth2LoginSuccessHandler oauth2SuccessHandler, OAuth2LoginFailureHandler oauth2FailureHandler) {
        http
                // ① CSRF: SPA용 설정(spa) + SameSite=Lax 쿠키 저장소. POST · PUT · PATCH · DELETE에 X-XSRF-TOKEN 헤더가 필요해집니다.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
                // ② 세션을 만들지 않습니다. 로그인 여부는 매 요청 쿠키의 Access Token으로 다시 확인합니다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 폼 로그인 · Basic 인증 · 기본 로그아웃 페이지는 쓰지 않습니다. (로그인은 소셜 로그인, 로그아웃은 /auth/logout API)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 인증 실패 후 원래 요청을 세션에 저장하는 기능. 세션을 쓰지 않으므로 끕니다.
                .requestCache(AbstractHttpConfigurer::disable)
                // ③ 쿠키 토큰 검증 필터. '비로그인 사용자' 표시를 붙이는 필터(Anonymous)보다 먼저 실행되어야 합니다.
                .addFilterBefore(new JwtCookieAuthenticationFilter(verifier), AnonymousAuthenticationFilter.class)
                // ③-2 카카오 · Google 소셜 로그인 (OAuth2). 시작 → 제공자 동의 화면 → 콜백 → 성공 · 실패 처리
                .oauth2Login(oauth2 -> oauth2
                        // 시작 주소 + 중간 상태(state)를 세션 대신 서명된 쿠키에 보관 (로그인 CSRF 방어)
                        // resolver: ?link=가 있으면 계정 연결로 시작 (연결 토큰 · 로그인 쿠키 확인)
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri(OAUTH2_AUTHORIZATION_BASE_URI)
                                .authorizationRequestResolver(authorizationRequestResolver)
                                .authorizationRequestRepository(authorizationRequestRepository))
                        // 시작 단계 실패(연결 거절 등)도 화면으로 돌려보냅니다. 설정하지 않으면 Spring 기본값인 500 응답이 나갑니다.
                        .withObjectPostProcessor(new ObjectPostProcessor<OAuth2AuthorizationRequestRedirectFilter>() {
                            @Override
                            public <O extends OAuth2AuthorizationRequestRedirectFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler(oauth2FailureHandler);
                                return filter;
                            }
                        })
                        .redirectionEndpoint(redirection -> redirection.baseUri(OAUTH2_CALLBACK_URI))
                        // 제공자가 준 Access Token은 쓰지 않으므로 저장하지 않습니다.
                        .authorizedClientRepository(new NoOpAuthorizedClientRepository())
                        .successHandler(oauth2SuccessHandler)
                        .failureHandler(oauth2FailureHandler))
                // ④ 로그인 필요(401) · 권한 없음 · CSRF 오류(403)를 JSON으로 응답합니다. (기본값은 로그인 페이지 리다이렉트)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // ⑤ 경로별 접근 규칙. 위에서부터 처음 맞는 규칙이 적용됩니다.
                .authorizeHttpRequests(authorize -> authorize
                        // 오류 응답을 만드는 내부 포워드(/error)는 막지 않아야 원래 오류(404 · 500 등)가 그대로 보입니다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 약관 전문: 가입 화면의 '보기'에서 로그인 전에 엽니다. 조회(GET)만 엽니다.
                        .requestMatchers(HttpMethod.GET, "/terms/**").permitAll()
                        // 피드 단건 조회 — 공유 링크로 비로그인 접근 허용 (목록·장소 조회는 로그인 필수)
                        .requestMatchers(HttpMethod.GET, "/feeds/*").permitAll()
                        // 관리자 API. 회원(ROLE_USER)은 403, 비로그인은 401. 공개로 열지 않습니다.
                        // 관리자 로그인 필터는 아직 없고, 인증된 AdminPrincipal(ROLE_ADMIN)만 통과합니다.
                        // POST의 CSRF 검사는 이 규칙보다 먼저라, 토큰이 없으면 403 INVALID_CSRF_TOKEN입니다.
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 나머지는 전부 로그인 필요. 새 API를 만들면 기본으로 보호됩니다.
                        .anyRequest().authenticated());
        return http.build();
    }

    /**
     * CSRF 토큰을 쿠키(XSRF-TOKEN)로 내려주는 저장소를 만듭니다.
     * <p>
     * 흐름: 서버가 응답에 XSRF-TOKEN 쿠키를 담아 줌 → 프론트 JS가 그 값을 읽어 X-XSRF-TOKEN 헤더로 보냄 → 서버가 둘을 비교.
     * 다른 사이트는 우리 쿠키를 읽을 수 없어서 헤더를 만들 수 없으므로 CSRF가 막힙니다.
     * <ul>
     *   <li>HttpOnly가 아님: 프론트 JS가 값을 읽어야 하기 때문 (Access Token 쿠키는 HttpOnly)</li>
     *   <li>SameSite=Lax: 인증 쿠키와 같은 정책. 다른 사이트의 POST에는 쿠키가 실리지 않음</li>
     *   <li>Secure: HTTPS 요청이면 자동으로 붙음</li>
     * </ul>
     */
    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }
}
