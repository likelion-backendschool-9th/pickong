package com.pickong.auth.adapter.in.security;

import com.pickong.auth.domain.vo.SocialProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 같은 OAuth 경로(시작 · 콜백)를 로그인과 <b>계정 연결</b>이 함께 쓰기 때문에, 이번 요청이 연결인지 구분하는 도구입니다.
 * <ol>
 *   <li>시작: 주소에 {@code ?link=연결 토큰}이 있으면 연결 모드. 확인을 마치면 회원 id를 OAuth 시작 정보의 attributes에 넣음
 *       → 서명된 state 쿠키에 함께 저장됨 ({@link LinkAwareAuthorizationRequestResolver})</li>
 *   <li>콜백: state 쿠키에서 시작 정보를 꺼낼 때 그 회원 id를 요청 속성에 옮겨 둠 ({@link #remember})</li>
 *   <li>성공 · 실패 처리기가 {@link #linkUserId}로 연결 모드인지 보고 이동할 화면을 정함</li>
 * </ol>
 * 회원 id가 서명된 쿠키 안에만 있어서 콜백 주소를 조작해 다른 회원에 연결할 수 없습니다.
 */
public final class OAuth2LinkRequests {

    /** OAuth 시작 주소의 연결 토큰 파라미터 */
    public static final String LINK_PARAM = "link";

    /** 연결 결과를 보여 줄 프론트 화면 */
    public static final String LOGIN_METHODS_PATH = "/settings/login-methods";

    /** 연결 시작 주소가 만료 · 위조됐거나 로그인한 회원과 다를 때 화면에 보내는 사유 */
    public static final String LINK_EXPIRED = "LINK_EXPIRED";

    /** OAuth 시작 정보 attributes에 넣는 연결 회원 id의 키 (state 쿠키에 함께 서명돼 저장) */
    static final String LINK_USER_ATTRIBUTE = "pickong_link_user_id";

    /** 콜백 요청 동안 연결 회원 id를 들고 있는 요청 속성 이름 */
    private static final String REQUEST_ATTRIBUTE = OAuth2LinkRequests.class.getName() + ".userId";

    private OAuth2LinkRequests() {
    }

    /** 프론트가 이동할 연결 시작 주소: /api/v1/auth/oauth2/authorization/{kakao|google}?link=토큰 (JWT라 주소에 그대로 써도 안전한 문자만 있음) */
    public static String authorizationUrl(SocialProvider provider, String linkToken) {
        return SecurityConfig.OAUTH2_AUTHORIZATION_BASE_URI + "/" + provider.slug() + "?" + LINK_PARAM + "=" + linkToken;
    }

    /** 콜백에서 꺼낸 시작 정보가 연결 모드면 회원 id를 이번 요청 속성에 옮겨 둡니다. (성공 · 실패 처리기가 읽음) */
    static void remember(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return;
        }
        Object userId = authorizationRequest.getAttribute(LINK_USER_ATTRIBUTE);
        if (userId != null) {
            request.setAttribute(REQUEST_ATTRIBUTE, Long.valueOf(userId.toString()));
        }
    }

    /** 이번 콜백이 계정 연결이면 연결할 회원 id */
    static Optional<Long> linkUserId(HttpServletRequest request) {
        return Optional.ofNullable((Long) request.getAttribute(REQUEST_ATTRIBUTE));
    }

    /** 연결 실패 시 이동할 화면: /settings/login-methods?error=사유 */
    static String failurePath(String reason) {
        return LOGIN_METHODS_PATH + "?error=" + reason;
    }
}
