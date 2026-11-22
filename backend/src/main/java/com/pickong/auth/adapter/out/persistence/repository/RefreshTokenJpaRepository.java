package com.pickong.auth.adapter.out.persistence.repository;

import com.pickong.auth.adapter.out.persistence.entity.RefreshTokenJpaEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    /** 아직 살아 있을 때만 폐기. 바뀐 행 수(0 또는 1)를 돌려줍니다. */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("now") OffsetDateTime now);

    /** family의 살아 있는 토큰을 모두 폐기. 이미 폐기된 토큰의 폐기 시각은 그대로 둡니다. */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.family = :family and t.revokedAt is null")
    int revokeFamily(@Param("family") String family, @Param("now") OffsetDateTime now);
}
