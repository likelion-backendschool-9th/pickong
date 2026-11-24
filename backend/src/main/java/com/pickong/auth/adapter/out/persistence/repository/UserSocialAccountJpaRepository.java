package com.pickong.auth.adapter.out.persistence.repository;

import com.pickong.auth.adapter.out.persistence.entity.UserSocialAccountJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSocialAccountJpaRepository extends JpaRepository<UserSocialAccountJpaEntity, Long> {

    Optional<UserSocialAccountJpaEntity> findByProviderAndProviderId(String provider, String providerId);

    /** lower(email) 인덱스를 타도록 대소문자 무시 비교. 같은 이메일이 여러 행이어도 하나만 있으면 됩니다. */
    Optional<UserSocialAccountJpaEntity> findFirstByEmailIgnoreCase(String email);

    /** 다른 회원의 소셜 계정에 이 이메일이 있는지 (대소문자 무시) */
    boolean existsByEmailIgnoreCaseAndUserIdNot(String email, Long userId);

    /** 이 회원의 연결 전부, 연결 순서(id)대로 */
    List<UserSocialAccountJpaEntity> findAllByUserIdOrderByIdAsc(Long userId);

    /** 한 회원의 소셜 연결을 모두 지웁니다 (익명화: 같은 소셜 계정으로 새로 가입할 수 있게) */
    @Modifying(clearAutomatically = true)
    @Query("delete from UserSocialAccountJpaEntity a where a.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
