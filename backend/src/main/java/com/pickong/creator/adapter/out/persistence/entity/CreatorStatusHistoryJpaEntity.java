package com.pickong.creator.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * creator_status_histories 테이블 JPA 엔티티.
 * 크리에이터 상태 변경 이력을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "creator_status_histories")
public class CreatorStatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /** 상태를 변경한 관리자 id. FK 여부 팀 확인. */
    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
