package com.pickong.moderation.adapter.out.persistence.entity;

import com.pickong.moderation.domain.model.Report;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * reports 테이블 JPA 엔티티.
 * 사용자가 제출한 신고를 저장한다.
 * target_id는 여러 테이블을 가리키므로 FK를 걸지 않는다.
 * handled_by는 admins 테이블이 생기기 전까지 FK가 없다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reports")
public class ReportJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    /** 신고 대상 종류. FEED, COMMENT, USER, COLLECTION */
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    /** 신고 대상 id. FK 없음 (여러 테이블 참조). */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    /** 처리 상태. PENDING, RESOLVED. 처리 액션과는 별개다. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 처리 액션. DISMISS, HIDE_CONTENT, SANCTION_USER. 대기 신고는 null. */
    @Column(name = "resolution_action", length = 30)
    private String resolutionAction;

    @Column(name = "resolution_memo", columnDefinition = "TEXT")
    private String resolutionMemo;

    /** 처리한 관리자 id. admins 테이블이 없어 FK는 걸지 않는다. */
    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private OffsetDateTime handledAt;

    /** 신고 시점의 대상 스냅샷. JSON 문자열. */
    @Column(name = "snapshot", columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 접수 행. 처리 정보는 비워 둡니다. */
    public static ReportJpaEntity forInsert(Report report) {
        ReportJpaEntity entity = new ReportJpaEntity();
        entity.reporterId = report.getReporterId();
        entity.targetType = report.getTargetType().name();
        entity.targetId = report.getTargetId();
        entity.reason = report.getReason();
        entity.detail = report.getDetail();
        entity.status = report.getStatus().name();
        entity.snapshot = report.getSnapshot();
        entity.createdAt = report.getCreatedAt();
        return entity;
    }

    /** 이미 검증된 기각 결과를 행에 반영합니다. 대기 행만 바꿉니다. */
    public void applyDismissal(String action, String memo, long adminId, OffsetDateTime handledAt) {
        if (!"PENDING".equals(status)) {
            throw new IllegalStateException("대기 신고만 기각할 수 있습니다.");
        }
        this.status = "RESOLVED";
        this.resolutionAction = action;
        this.resolutionMemo = memo;
        this.handledBy = adminId;
        this.handledAt = handledAt;
    }
}
