package com.pickong.moderation.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * reports 테이블 JPA 엔티티.
 * 사용자가 제출한 신고를 저장한다.
 * target_id는 여러 테이블을 가리키므로 FK를 걸지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reports")
public class ReportsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    /** 신고 대상 종류. 값 목록: FEED, COMMENT, USER, COLLECTION 등 */
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    /** 신고 대상 id. FK 없음 (여러 테이블 참조). */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    /** 처리 상태. 값 목록: PENDING, HANDLED, DISMISSED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 처리한 관리자 id. FK 여부 팀 확인. */
    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private OffsetDateTime handledAt;

    /** 신고 시점의 대상 스냅샷. JSONB → String 저장. */
    @Column(name = "snapshot", columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
