package com.pickong.auth.adapter.out.persistence;

import com.pickong.auth.adapter.out.persistence.entity.UserSocialAccountJpaEntity;
import com.pickong.auth.adapter.out.persistence.repository.UserSocialAccountJpaRepository;
import com.pickong.auth.application.dto.LinkedSocialAccount;
import com.pickong.auth.application.port.out.SocialAccountPersistencePort;
import com.pickong.auth.domain.AuthErrorCode;
import com.pickong.auth.domain.vo.SocialProvider;
import com.pickong.common.error.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 소셜 계정 연결 저장소. JPA 엔티티는 이 어댑터 밖으로 내보내지 않고 dto로 바꿔서 돌려줍니다. */
@Component
public class SocialAccountPersistenceAdapter implements SocialAccountPersistencePort {

    /** V1의 UNIQUE(provider, provider_id) 제약 이름 */
    private static final String PROVIDER_ID_UNIQUE = "uk_user_social_accounts_provider_id";

    private final UserSocialAccountJpaRepository repository;
    private final Clock clock;

    public SocialAccountPersistenceAdapter(UserSocialAccountJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<LinkedSocialAccount> findByProvider(SocialProvider provider, String providerUserId) {
        return repository.findByProviderAndProviderId(provider.name(), providerUserId).map(UserSocialAccountJpaEntity::toDto);
    }

    @Override
    public Optional<LinkedSocialAccount> findAnyByEmail(String email) {
        return repository.findFirstByEmailIgnoreCase(email).map(UserSocialAccountJpaEntity::toDto);
    }

    /** saveAndFlush로 바로 INSERT해서 UNIQUE 위반을 여기서 알맞은 오류 코드로 바꿉니다. */
    @Override
    public void link(Long userId, SocialProvider provider, String providerUserId, String email) {
        try {
            repository.saveAndFlush(UserSocialAccountJpaEntity.link(userId, provider.name(), providerUserId, email, OffsetDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(PROVIDER_ID_UNIQUE)) {
                throw new BusinessException(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
            }
            throw e;
        }
    }
}
