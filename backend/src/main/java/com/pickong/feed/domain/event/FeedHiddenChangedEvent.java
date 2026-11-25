package com.pickong.feed.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 실제 숨김 상태 변경. 같은 트랜잭션의 moderation 리스너가 작업 기록을 담당합니다. */
public record FeedHiddenChangedEvent(long feedId, UUID publicId, boolean hidden, String reason,
        long adminId, Long sourceReportId, OffsetDateTime occurredAt) {
}
