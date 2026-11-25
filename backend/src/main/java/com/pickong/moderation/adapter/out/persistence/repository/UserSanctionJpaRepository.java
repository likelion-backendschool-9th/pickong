package com.pickong.moderation.adapter.out.persistence.repository;

import com.pickong.moderation.adapter.out.persistence.entity.UserSanctionJpaEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSanctionJpaRepository extends JpaRepository<UserSanctionJpaEntity, Long> {
    Optional<UserSanctionJpaEntity> findBySourceReportId(long sourceReportId);

    List<UserSanctionJpaEntity> findByUserIdOrderByCreatedAtDescIdDesc(long userId);

    List<UserSanctionJpaEntity> findByUserIdAndEndedAtIsNullOrderByCreatedAtAscIdAsc(long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UserSanctionJpaEntity s
               set s.endedAt = :endedAt, s.endReason = :endReason, s.revokedAt = :revokedAt,
                   s.revokedByAdminId = :revokedByAdminId, s.revokeReason = :revokeReason
             where s.id = :id and s.userId = :userId and s.endedAt is null
            """)
    int endIfOpen(@Param("id") long id, @Param("userId") long userId,
            @Param("endedAt") OffsetDateTime endedAt, @Param("endReason") String endReason,
            @Param("revokedAt") OffsetDateTime revokedAt, @Param("revokedByAdminId") Long revokedByAdminId,
            @Param("revokeReason") String revokeReason);
}
