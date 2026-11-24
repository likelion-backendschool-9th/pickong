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

    /** 한 회원의 살아 있는 토큰을 모두 폐기 (탈퇴 신청: 모든 기기에서 로그아웃) */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /** 한 회원의 토큰 행을 모두 지웁니다 (익명화) */
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshTokenJpaEntity t where t.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /** 만료 시각이 기준보다 이전인 행을 한 번에 지웁니다. 지운 행 수를 돌려줍니다. */
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshTokenJpaEntity t where t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") OffsetDateTime threshold);
}
