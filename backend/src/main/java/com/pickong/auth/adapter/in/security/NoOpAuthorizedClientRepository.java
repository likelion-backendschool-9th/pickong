package com.pickong.auth.adapter.in.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 카카오 · Google이 준 Access Token(OAuth2AuthorizedClient)을 <b>저장하지 않는</b> 저장소.
 * <p>
 * 찜콩은 로그인할 때 사용자 정보만 받고, 그 뒤로 카카오 · Google API를 대신 호출하지 않습니다.
 * Spring 기본값은 이 토큰을 세션이나 메모리에 계속 쌓아 두는데, 쓰지 않는 외부 토큰을 들고 있을 이유가 없고
 * 메모리에 계속 쌓이는 문제도 있어서 버립니다.
 */
public class NoOpAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, Authentication principal,
            HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
        // 저장하지 않습니다.
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, Authentication principal, HttpServletRequest request,
            HttpServletResponse response) {
        // 저장한 것이 없습니다.
    }
}
