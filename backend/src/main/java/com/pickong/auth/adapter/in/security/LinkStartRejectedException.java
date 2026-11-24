package com.pickong.auth.adapter.in.security;

/**
 * 연결 시작을 거절할 때 던집니다. (연결 토큰이 만료 · 위조됐거나, 로그인 쿠키가 없거나, 토큰의 회원과 다름)
 * Spring의 OAuth 시작 필터가 잡아서 {@link OAuth2LoginFailureHandler}로 넘기고, 처리기는 화면에 LINK_EXPIRED로 안내합니다.
 * 원인은 화면에 드러내지 않고 서버 로그에만 남깁니다.
 */
class LinkStartRejectedException extends RuntimeException {

    LinkStartRejectedException(String reason) {
        super(reason);
    }
}
