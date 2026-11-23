package com.pickong.auth.adapter.out.persistence.repository;

import com.pickong.auth.adapter.out.persistence.entity.UserSocialAccountJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSocialAccountJpaRepository extends JpaRepository<UserSocialAccountJpaEntity, Long> {

    Optional<UserSocialAccountJpaEntity> findByProviderAndProviderId(String provider, String providerId);

    /** lower(email) 인덱스를 타도록 대소문자 무시 비교. 같은 이메일이 여러 행이어도 하나만 있으면 됩니다. */
    Optional<UserSocialAccountJpaEntity> findFirstByEmailIgnoreCase(String email);
}
