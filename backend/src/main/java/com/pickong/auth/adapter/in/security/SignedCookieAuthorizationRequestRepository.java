package com.pickong.auth.adapter.in.security;

import com.pickong.auth.adapter.out.external.jwt.JwtCodec;
import com.pickong.auth.adapter.out.external.jwt.JwtCodec.TokenType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * OAuth 로그인 중간 상태(OAuth2AuthorizationRequest)를 서버 세션 대신 <b>서명된 쿠키</b>(pickong_oauth, 10분)에 보관합니다.
 * <p>
 * 흐름:
 * <ol>
 *   <li>로그인 시작: Spring이 state(무작위 값)를 만들고 {@link #saveAuthorizationRequest}를 부름 → 쿠키에 서명해서 저장</li>
 *   <li>카카오 · Google 동의 화면</li>
 *   <li>콜백: Spring이 {@link #removeAuthorizationRequest}를 부름 → 쿠키를 읽어 서명 · 만료를 확인하고,
 *       <b>쿠키의 state와 주소의 state가 같을 때만</b> 돌려줌. 다르면 null → 로그인 실패</li>
 * </ol>
 * 이 브라우저가 직접 시작한 로그인만 끝까지 갈 수 있어서, 남이 만든 콜백 링크로 공격자 계정에 로그인시키는 로그인 CSRF가 막힙니다.
 * 서명이 있어 쿠키 내용을 바꿀 수 없고, 서버가 아무것도 기억하지 않으므로 서버를 여러 대로 늘려도 동작합니다.
 */
@Component
public class SignedCookieAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    /** 동의 화면에서 머무를 수 있는 시간. 넘으면 다시 로그인을 시작해야 합니다. */
    static final Duration TTL = Duration.ofMinutes(10);

    private final JwtCodec codec;
    private final TokenCookieWriter cookieWriter;

    public SignedCookieAuthorizationRequestRepository(JwtCodec codec, TokenCookieWriter cookieWriter) {
        this.codec = codec;
        this.cookieWriter = cookieWriter;
    }

    /**
     * 콜백 요청에 맞는 로그인 시작 정보를 쿠키에서 꺼냅니다.
     *
     * @return 서명 · 만료가 맞고 state가 일치하면 시작 정보, 아니면 null
     */
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String cookie = readCookie(request);
        if (cookie == null) {
            return null;
        }
        OAuth2AuthorizationRequest saved = codec.decode(TokenType.OAUTH_STATE, cookie)
                .map(SignedCookieAuthorizationRequestRepository::toAuthorizationRequest)
                .orElse(null);
        if (saved == null || !Objects.equals(saved.getState(), request.getParameter("state"))) {
            return null;
        }
        return saved;
    }

    /** 로그인 시작 정보를 서명해서 쿠키에 씁니다. null이면(저장할 것이 없으면) 쿠키를 지웁니다. */
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            cookieWriter.clearOAuthState(response);
            return;
        }
        cookieWriter.writeOAuthState(response, toToken(authorizationRequest), TTL);
    }

    /**
     * 콜백에서 한 번 꺼내고 쿠키는 바로 지웁니다. 같은 state로 두 번 로그인할 수 없게 하기 위해서입니다.
     * 계정 연결로 시작한 것이면 연결할 회원 id를 이번 요청에 옮겨 둡니다(성공 · 실패 처리기가 읽음).
     */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest saved = loadAuthorizationRequest(request);
        cookieWriter.clearOAuthState(response);
        OAuth2LinkRequests.remember(request, saved);
        return saved;
    }

    private static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookies.OAUTH_STATE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 시작 정보를 서명 토큰으로 만듭니다. state는 sub에, 나머지는 클레임에 담습니다.
     * (OIDC인 Google은 attributes · additionalParameters에 nonce가 들어 있어 그대로 보관해야 ID 토큰 검증이 됩니다)
     */
    private String toToken(OAuth2AuthorizationRequest request) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("authorization_uri", request.getAuthorizationUri());
        claims.put("client_id", request.getClientId());
        claims.put("redirect_uri", request.getRedirectUri());
        claims.put("scopes", List.copyOf(request.getScopes()));
        claims.put("authorization_request_uri", request.getAuthorizationRequestUri());
        claims.put("additional_parameters", new HashMap<>(request.getAdditionalParameters()));
        claims.put("attributes", new HashMap<>(request.getAttributes()));
        return codec.encode(TokenType.OAUTH_STATE, request.getState(), claims, TTL);
    }

    /** 서명 토큰을 다시 시작 정보로 되돌립니다. */
    private static OAuth2AuthorizationRequest toAuthorizationRequest(Jwt jwt) {
        List<String> scopes = jwt.getClaimAsStringList("scopes");
        Map<String, Object> additionalParameters = jwt.getClaimAsMap("additional_parameters");
        Map<String, Object> attributes = jwt.getClaimAsMap("attributes");
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(jwt.getClaimAsString("authorization_uri"))
                .clientId(jwt.getClaimAsString("client_id"))
                .redirectUri(jwt.getClaimAsString("redirect_uri"))
                .scopes(scopes == null ? Set.of() : new LinkedHashSet<>(scopes))
                .state(jwt.getSubject())
                .additionalParameters(additionalParameters == null ? Map.of() : additionalParameters)
                .attributes(attributes == null ? Map.of() : attributes)
                .authorizationRequestUri(jwt.getClaimAsString("authorization_request_uri"))
                .build();
    }
}
