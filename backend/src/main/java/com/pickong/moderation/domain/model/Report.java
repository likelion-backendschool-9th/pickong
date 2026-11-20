package com.pickong.moderation.domain.model;

import com.pickong.moderation.domain.vo.ReportStatus;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 신고 애그리거트. targetId는 여러 테이블을 가리킵니다.
 * <p>snapshot은 신고 시점의 대상 스냅샷(JSON)입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Report {
    private Long id;
    private Long reporterId;
    private ReportTargetType targetType;
    private Long targetId;
    private String reason;
    private String detail;
    private ReportStatus status;
    private Long handledBy;
    private OffsetDateTime handledAt;
    private String snapshot;
    private OffsetDateTime createdAt;
}
