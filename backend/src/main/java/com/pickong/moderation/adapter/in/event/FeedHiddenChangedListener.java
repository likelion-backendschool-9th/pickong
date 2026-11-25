package com.pickong.moderation.adapter.in.event;

import com.pickong.admin.application.dto.AdminActionLogEntry;
import com.pickong.admin.application.port.in.AdminActionLogUseCase;
import com.pickong.feed.domain.event.FeedHiddenChangedEvent;
import com.pickong.feed.domain.event.CommentHiddenChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 기존 moderation → feed·admin 의존 방향을 유지하는 동기 작업 기록. 실패하면 상태 변경도 롤백합니다. */
@Component
public class FeedHiddenChangedListener {
    private final AdminActionLogUseCase logs;

    public FeedHiddenChangedListener(AdminActionLogUseCase logs) { this.logs = logs; }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(FeedHiddenChangedEvent event) {
        logs.record(new AdminActionLogEntry(event.adminId(), event.hidden() ? "HIDE_CONTENT" : "RESTORE_CONTENT", "FEED",
                event.feedId(), event.reason(), "{\"publicId\":\"" + event.publicId() + "\",\"hidden\":" + event.hidden() + "}",
                event.sourceReportId(), event.occurredAt()));
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(CommentHiddenChangedEvent event) {
        logs.record(new AdminActionLogEntry(event.adminId(), event.hidden() ? "HIDE_CONTENT" : "RESTORE_CONTENT", "COMMENT",
                event.commentId(), event.reason(), "{\"feedId\":" + event.feedId() + ",\"hidden\":" + event.hidden() + "}",
                event.sourceReportId(), event.occurredAt()));
    }
}
