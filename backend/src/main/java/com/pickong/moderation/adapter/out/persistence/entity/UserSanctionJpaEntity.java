package com.pickong.moderation.adapter.out.persistence.entity;

import com.pickong.moderation.domain.model.Sanction;
import com.pickong.moderation.domain.vo.SanctionEndReason;
import com.pickong.moderation.domain.vo.SanctionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * user_sanctions 테이블 JPA 엔티티.
 * 사용자 제재 이력을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_sanctions")
public class UserSanctionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 제재 유형. 값 목록: WARNING, SUSPEND, BAN */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** 정지 해제 일시. SUSPEND 제재 시에만 값이 있음. */
    @Column(name = "suspended_until")
    private OffsetDateTime suspendedUntil;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by_admin_id")
    private Long revokedByAdminId;

    @Column(name = "revoke_reason", columnDefinition = "TEXT")
    private String revokeReason;

    @Column(name = "source_report_id")
    private Long sourceReportId;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "end_reason", length = 30)
    private String endReason;

    /** 처리한 관리자 id. admins FK. */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 새 이력만 INSERT합니다. 기존 행은 종료 필드만 전용 쿼리로 갱신합니다. */
    public static UserSanctionJpaEntity forInsert(Sanction sanction) {
        if (sanction.getId() != null || sanction.getEndedAt() != null || sanction.getEndReason() != null
                || sanction.getRevokedAt() != null || sanction.getRevokedByAdminId() != null || sanction.getRevokeReason() != null) {
            throw new IllegalArgumentException("새 미종료 제재만 저장할 수 있습니다.");
        }
        UserSanctionJpaEntity entity = new UserSanctionJpaEntity();
        entity.userId = sanction.getUserId();
        entity.type = sanction.getType().name();
        entity.reason = sanction.getReason();
        entity.suspendedUntil = sanction.getSuspendedUntil();
        entity.adminId = sanction.getAdminId();
        entity.sourceReportId = sanction.getSourceReportId();
        entity.createdAt = sanction.getCreatedAt();
        return entity;
    }

    public Sanction toDomain() {
        return Sanction.builder().id(id).userId(userId).type(SanctionType.valueOf(type)).reason(reason)
                .suspendedUntil(suspendedUntil).adminId(adminId).createdAt(createdAt)
                .revokedAt(revokedAt).revokedByAdminId(revokedByAdminId).revokeReason(revokeReason)
                .sourceReportId(sourceReportId).endedAt(endedAt)
                .endReason(endReason == null ? null : SanctionEndReason.valueOf(endReason)).build();
    }
}
