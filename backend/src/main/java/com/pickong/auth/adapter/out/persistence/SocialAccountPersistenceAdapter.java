package com.pickong.auth.adapter.out.persistence;

import com.pickong.auth.adapter.out.persistence.entity.UserSocialAccountJpaEntity;
import com.pickong.auth.adapter.out.persistence.repository.UserSocialAccountJpaRepository;
import com.pickong.auth.application.dto.LinkedSocialAccount;
import com.pickong.auth.application.port.out.SocialAccountPersistencePort;
import com.pickong.auth.domain.vo.SocialProvider;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 소셜 계정 연결 저장소. JPA 엔티티는 이 어댑터 밖으로 내보내지 않고 dto로 바꿔서 돌려줍니다. */
@Component
public class SocialAccountPersistenceAdapter implements SocialAccountPersistencePort {

    private final UserSocialAccountJpaRepository repository;

    public SocialAccountPersistenceAdapter(UserSocialAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<LinkedSocialAccount> findByProvider(SocialProvider provider, String providerUserId) {
        return repository.findByProviderAndProviderId(provider.name(), providerUserId).map(UserSocialAccountJpaEntity::toDto);
    }

    @Override
    public Optional<LinkedSocialAccount> findAnyByEmail(String email) {
        return repository.findFirstByEmailIgnoreCase(email).map(UserSocialAccountJpaEntity::toDto);
    }
}
