package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.application.dto.AdminFeedView;
import com.pickong.feed.application.port.in.FeedModerationUseCase;
import com.pickong.feed.application.port.out.AdminFeedQueryPort;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.application.port.out.CommentRepository;
import com.pickong.feed.domain.model.Comment;
import com.pickong.feed.domain.event.CommentHiddenChangedEvent;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.domain.event.FeedHiddenChangedEvent;
import com.pickong.feed.domain.model.Feed;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FeedModerationService implements FeedModerationUseCase {
    private final FeedRepository feeds;
    private final AdminFeedQueryPort query;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final CommentRepository comments;

    public FeedModerationService(FeedRepository feeds, AdminFeedQueryPort query, ApplicationEventPublisher events, Clock clock,
            CommentRepository comments) {
        this.feeds = feeds;
        this.query = query;
        this.events = events;
        this.clock = clock;
        this.comments = comments;
    }

    @Override
    public AdminFeedView setHidden(UUID publicId, boolean hidden, String reason, long adminId) {
        change(feeds.findByPublicIdForUpdate(publicId), hidden, reason, adminId, null);
        return query.findByPublicId(publicId).orElseThrow(() -> new BusinessException(FeedErrorCode.FEED_NOT_FOUND));
    }

    @Override
    public boolean setHidden(long feedId, boolean hidden, String reason, long adminId, Long sourceReportId) {
        return change(feeds.findByIdForUpdate(feedId), hidden, reason, adminId, sourceReportId);
    }

    @Override
    public boolean setCommentHidden(long commentId, boolean hidden, String reason, long adminId, Long sourceReportId) {
        if (adminId <= 0 || (sourceReportId != null && sourceReportId <= 0)) throw new IllegalArgumentException("유효한 처리자와 원인 ID가 필요합니다.");
        String normalized = Feed.normalizeModerationReason(reason, hidden);
        long feedId = comments.findById(commentId).getFeedId();
        // 작성·수정·삭제·반응과 모두 부모 게시물 → 댓글 순서로 잠급니다.
        Feed parent = feeds.findByIdForUpdate(feedId);
        if (parent.getDeletedAt() != null) throw new BusinessException(FeedErrorCode.FEED_NOT_FOUND);
        Comment target = comments.findByIdForUpdate(commentId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime previous = target.getHiddenAt();
        if (!target.changeHidden(hidden, now)) return false;
        if (!comments.updateHiddenAt(commentId, previous, target.getHiddenAt(), now)) {
            throw new BusinessException(FeedErrorCode.FEED_STATE_CONFLICT);
        }
        events.publishEvent(new CommentHiddenChangedEvent(commentId, feedId, hidden, normalized, adminId, sourceReportId, now));
        return true;
    }

    private boolean change(Feed feed, boolean hidden, String reason, long adminId, Long sourceReportId) {
        if (adminId <= 0 || (sourceReportId != null && sourceReportId <= 0)) throw new IllegalArgumentException("유효한 처리자와 원인 ID가 필요합니다.");
        String normalized = Feed.normalizeModerationReason(reason, hidden);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime previous = feed.getHiddenAt();
        if (!feed.changeHidden(hidden, now)) return false;
        if (!feeds.updateHiddenAt(feed.getId(), previous, feed.getHiddenAt(), now)) {
            throw new BusinessException(FeedErrorCode.FEED_STATE_CONFLICT);
        }
        events.publishEvent(new FeedHiddenChangedEvent(feed.getId(), feed.getPublicId(), hidden, normalized, adminId, sourceReportId, now));
        return true;
    }
}
