package com.pickong.moderation.adapter.out.persistence.repository;

import com.pickong.moderation.adapter.out.persistence.entity.ReportJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportJpaRepository extends JpaRepository<ReportJpaEntity, Long> {

    List<ReportJpaEntity> findByStatusOrderByCreatedAtDescIdDesc(String status);

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
            Long reporterId, String targetType, Long targetId, String status);
}
