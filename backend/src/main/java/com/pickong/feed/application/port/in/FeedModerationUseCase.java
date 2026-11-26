package com.pickong.feed.application.port.in;

import com.pickong.feed.application.dto.AdminFeedView;
import java.util.UUID;

public interface FeedModerationUseCase {
    AdminFeedView setHidden(UUID publicId, boolean hidden, String reason, long adminId);

    /** 신고 처리용 내부 ID 입구. 반환값은 실제 상태 변경 여부입니다. */
    boolean setHidden(long feedId, boolean hidden, String reason, long adminId, Long sourceReportId);

    boolean setCommentHidden(long commentId, boolean hidden, String reason, long adminId, Long sourceReportId);
}
