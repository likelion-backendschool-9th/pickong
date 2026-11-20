package com.pickong.creator.domain.model;

import com.pickong.creator.domain.vo.CreatorStatus;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 크리에이터 애그리거트. 신청 · 승인 · 거절 · 박탈, 재신청. 회원당 1개입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Creator {
    private Long id;
    private Long userId;
    private CreatorStatus status;
    private Long gradeId;
    private OffsetDateTime appliedAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime suspendedAt;
    private String rejectionReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
