package com.pickong.auth.application.port.out;

import com.pickong.auth.application.dto.ConnectedSocialAccount;
import com.pickong.auth.application.dto.LinkedSocialAccount;
import com.pickong.auth.domain.vo.SocialProvider;
import java.util.List;
import java.util.Optional;

/** 소셜 계정 연결(user_social_accounts) 저장소 포트. 구현: {@code auth.adapter.out.persistence.SocialAccountPersistenceAdapter} */
public interface SocialAccountPersistencePort {

    /** 이 소셜 계정이 연결된 찜콩 회원을 찾습니다. ((provider, provider_id)는 UNIQUE라 최대 하나) */
    Optional<LinkedSocialAccount> findByProvider(SocialProvider provider, String providerUserId);

    /** 이 이메일(대소문자 무시)이 연결된 소셜 계정 하나를 찾습니다. 1인 1계정 확인용 */
    Optional<LinkedSocialAccount> findAnyByEmail(String email);

    /** 다른 회원(userId가 아닌)의 소셜 계정 중 이 이메일(대소문자 무시)이 있는지. 계정 연결 때 1인 1계정 확인용 */
    boolean existsEmailOnOtherUser(String email, Long userId);

    /** 이 회원에 연결된 소셜 계정 전부, 연결 순서(id 오름차순)대로. 첫 번째가 가입할 때 쓴 수단입니다. */
    List<ConnectedSocialAccount> findAllByUserId(Long userId);

    /**
     * 소셜 계정을 찜콩 회원에 연결합니다. 동시에 같은 연결이 두 번 들어오면 DB UNIQUE가 막고 오류 코드로 바꿔 던집니다.
     * <ul>
     *   <li>UNIQUE(provider, provider_id) 위반 → SOCIAL_ACCOUNT_ALREADY_LINKED (이 소셜 계정은 이미 어떤 회원에 연결됨)</li>
     *   <li>UNIQUE(user_id, provider) 위반 → PROVIDER_ALREADY_LINKED (이 회원에 같은 제공자가 이미 연결됨)</li>
     * </ul>
     */
    void link(Long userId, SocialProvider provider, String providerUserId, String email);

    /** 연결 한 줄을 지웁니다. */
    void delete(Long socialAccountId);
}
