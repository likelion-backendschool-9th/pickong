package com.pickong.admin.adapter.out.persistence.entity;

import com.pickong.admin.application.dto.AdminActionLogEntry;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * admin_action_logs 테이블 JPA 엔티티.
 * 관리자의 작업 이력을 저장한다.
 * target_id는 여러 테이블을 가리키므로 FK를 걸지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin_action_logs")
public class AdminActionLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    /** 작업 종류. 신고 처리·제재·승인·정산 확정 등 */
    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "target_type")
    private String targetType;

    /** FK 없음 (여러 테이블 참조). */
    @Column(name = "target_id")
    private Long targetId;

    /** 별도 JSON 변환을 강제하지 않는 TEXT 상세 정보. */
    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "source_report_id")
    private Long sourceReportId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static AdminActionLogJpaEntity forInsert(AdminActionLogEntry entry) {
        AdminActionLogJpaEntity entity = new AdminActionLogJpaEntity();
        entity.adminId = entry.adminId();
        entity.actionType = entry.actionType();
        entity.targetType = entry.targetType();
        entity.targetId = entry.targetId();
        entity.reason = entry.reason();
        entity.detail = entry.detail();
        entity.sourceReportId = entry.sourceReportId();
        entity.createdAt = entry.createdAt();
        return entity;
    }
}
