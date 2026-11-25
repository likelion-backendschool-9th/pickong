package com.pickong.feed.domain.event;

import java.time.OffsetDateTime;

public record CommentHiddenChangedEvent(long commentId, long feedId, boolean hidden, String reason,
        long adminId, Long sourceReportId, OffsetDateTime occurredAt) {
}
