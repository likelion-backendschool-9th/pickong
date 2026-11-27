package com.pickong.moderation.adapter.out.persistence.query;

import com.pickong.common.domain.Visibility;
import com.pickong.moderation.application.dto.ReportTargetLookup;
import com.pickong.moderation.application.port.out.ReportTargetLookupPort;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 신고 접수 전에 게시물 · 댓글을 읽습니다. feed · social 엔티티는 주입하지 않습니다.
 * 없거나 삭제 · 숨김이면 신고 대상이 아니라 빈 값입니다. 공개 범위를 알 수 없는 게시물도 신고 대상이 아닙니다.
 * 신고자가 볼 수 있는지(공개 범위, 팔로우)는 여기서 판단하지 않습니다. 같은 규칙이 두 곳에 생기지 않도록
 * 접수 서비스가 feed 모듈의 FeedAccessQueryUseCase에 물어봅니다. 자기 신고 거절도 접수 서비스가 합니다.
 */
@Component
public class ReportTargetLookupAdapter implements ReportTargetLookupPort {

    private final JdbcTemplate jdbc;

    public ReportTargetLookupAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReportTargetLookup> findReportable(ReportTargetType targetType, long targetId) {
        if (targetId <= 0 || targetType == null) {
            return Optional.empty();
        }
        return switch (targetType) {
            case FEED -> findFeed(targetId);
            case COMMENT -> findComment(targetId);
            case USER, COLLECTION -> Optional.empty();
        };
    }

    private Optional<ReportTargetLookup> findFeed(long targetId) {
        return jdbc.query("""
                SELECT user_id, content, visibility, hidden_at, deleted_at
                  FROM feeds
                 WHERE id = ?
                """, (rs, row) -> feed(targetId, rs), targetId).stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    private Optional<ReportTargetLookup> findComment(long targetId) {
        return jdbc.query("""
                SELECT c.user_id AS author_id, c.content, c.hidden_at, c.deleted_at, c.feed_id,
                       f.visibility AS feed_visibility, f.hidden_at AS feed_hidden_at, f.deleted_at AS feed_deleted_at
                  FROM comments c
                  JOIN feeds f ON f.id = c.feed_id
                 WHERE c.id = ?
                """, (rs, row) -> comment(targetId, rs), targetId).stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static ReportTargetLookup feed(long targetId, ResultSet rs) throws SQLException {
        if (!reportableFeed(rs.getString("visibility"), rs.getTimestamp("hidden_at"), rs.getTimestamp("deleted_at"))) {
            return null;
        }
        return new ReportTargetLookup(ReportTargetType.FEED, targetId, rs.getLong("user_id"), rs.getString("content"), targetId);
    }

    private static ReportTargetLookup comment(long targetId, ResultSet rs) throws SQLException {
        if (rs.getTimestamp("hidden_at") != null || rs.getTimestamp("deleted_at") != null) {
            return null;
        }
        if (!reportableFeed(rs.getString("feed_visibility"), rs.getTimestamp("feed_hidden_at"), rs.getTimestamp("feed_deleted_at"))) {
            return null;
        }
        return new ReportTargetLookup(ReportTargetType.COMMENT, targetId, rs.getLong("author_id"), rs.getString("content"),
                rs.getLong("feed_id"));
    }

    /** 삭제 · 숨김 게시물과, 공개 범위 값을 알 수 없는 게시물은 신고 대상이 아님 */
    private static boolean reportableFeed(String visibility, Timestamp hiddenAt, Timestamp deletedAt) {
        return hiddenAt == null && deletedAt == null && visibilityOf(visibility) != null;
    }

    private static Visibility visibilityOf(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Visibility.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
