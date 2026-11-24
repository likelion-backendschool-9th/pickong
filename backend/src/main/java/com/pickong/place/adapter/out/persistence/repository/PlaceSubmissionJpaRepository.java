package com.pickong.place.adapter.out.persistence.repository;

import com.pickong.place.adapter.out.persistence.entity.PlaceSubmissionJpaEntity;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceSubmissionJpaRepository extends JpaRepository<PlaceSubmissionJpaEntity, Long> {

    /**
     * 대기 행만 심사 결과로 바꿉니다. 좌표는 접수 때 저장된 값을 유지합니다.
     * 이미 처리된 행은 0을 돌려줘서 공개 장소가 두 번 연결되지 않게 합니다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE place_submissions
               SET status = :status,
                   reviewer_admin_id = :reviewerAdminId,
                   reviewed_at = :reviewedAt,
                   reject_reason = :rejectReason,
                   place_id = :placeId
             WHERE id = :id
               AND status = 'PENDING'
            """, nativeQuery = true)
    int updateIfPending(@Param("id") long id, @Param("status") String status,
            @Param("reviewerAdminId") Long reviewerAdminId, @Param("reviewedAt") OffsetDateTime reviewedAt,
            @Param("rejectReason") String rejectReason, @Param("placeId") Long placeId);
}
