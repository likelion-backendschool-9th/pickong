package com.pickong.auth.application.port.out;

import com.pickong.auth.application.dto.LinkedSocialAccount;
import com.pickong.auth.domain.vo.SocialProvider;
import java.util.Optional;

/** 소셜 계정 연결(user_social_accounts) 저장소 포트. 구현: {@code auth.adapter.out.persistence.SocialAccountPersistenceAdapter} */
public interface SocialAccountPersistencePort {

    /** 이 소셜 계정이 연결된 찜콩 회원을 찾습니다. ((provider, provider_id)는 UNIQUE라 최대 하나) */
    Optional<LinkedSocialAccount> findByProvider(SocialProvider provider, String providerUserId);

    /** 이 이메일(대소문자 무시)이 연결된 소셜 계정 하나를 찾습니다. 1인 1계정 확인용 */
    Optional<LinkedSocialAccount> findAnyByEmail(String email);

    /**
     * 소셜 계정을 찜콩 회원에 연결합니다.
     * 같은 소셜 계정이 동시에 두 번 연결되려 하면 DB UNIQUE(provider, provider_id)가 막고, SOCIAL_ACCOUNT_ALREADY_LINKED로 바꿔 던집니다.
     */
    void link(Long userId, SocialProvider provider, String providerUserId, String email);
}
