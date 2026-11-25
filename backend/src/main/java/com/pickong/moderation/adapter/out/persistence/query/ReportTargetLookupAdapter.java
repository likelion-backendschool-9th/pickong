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
 * 삭제 · 숨김은 신고 대상이 아닙니다. 다른 회원의 글은 공개 범위가 PUBLIC일 때만 반환합니다.
 * 작성자 본인의 글은 공개 범위와 관계없이 작성자 id를 돌려줍니다. 자기 신고 거절은 접수 서비스가 합니다.
 * 팔로우 목록과 차단은 콘텐츠 접근 조회에 쓰이지 않아 여기서도 보지 않습니다.
 */
@Component
public class ReportTargetLookupAdapter implements ReportTargetLookupPort {

    private final JdbcTemplate jdbc;

    public ReportTargetLookupAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReportTargetLookup> findReportable(long reporterId, ReportTargetType targetType, long targetId) {
        if (reporterId <= 0 || targetId <= 0 || targetType == null) {
            return Optional.empty();
        }
        return switch (targetType) {
            case FEED -> findFeed(reporterId, targetId);
            case COMMENT -> findComment(reporterId, targetId);
            case USER, COLLECTION -> Optional.empty();
        };
    }

    private Optional<ReportTargetLookup> findFeed(long reporterId, long targetId) {
        return jdbc.query("""
                SELECT user_id, content, visibility, hidden_at, deleted_at
                  FROM feeds
                 WHERE id = ?
                """, (rs, row) -> feed(reporterId, targetId, rs), targetId).stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    private Optional<ReportTargetLookup> findComment(long reporterId, long targetId) {
        return jdbc.query("""
                SELECT c.user_id AS author_id, c.content, c.hidden_at, c.deleted_at,
                       f.user_id AS feed_author_id, f.visibility AS feed_visibility,
                       f.hidden_at AS feed_hidden_at, f.deleted_at AS feed_deleted_at
                  FROM comments c
                  JOIN feeds f ON f.id = c.feed_id
                 WHERE c.id = ?
                """, (rs, row) -> comment(reporterId, targetId, rs), targetId).stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static ReportTargetLookup feed(long reporterId, long targetId, ResultSet rs) throws SQLException {
        long authorId = rs.getLong("user_id");
        if (!visibleTo(reporterId, authorId, rs.getString("visibility"), rs.getTimestamp("hidden_at"), rs.getTimestamp("deleted_at"))) {
            return null;
        }
        return new ReportTargetLookup(ReportTargetType.FEED, targetId, authorId, rs.getString("content"));
    }

    private static ReportTargetLookup comment(long reporterId, long targetId, ResultSet rs) throws SQLException {
        if (rs.getTimestamp("hidden_at") != null || rs.getTimestamp("deleted_at") != null) {
            return null;
        }
        if (!visibleTo(reporterId, rs.getLong("feed_author_id"), rs.getString("feed_visibility"),
                rs.getTimestamp("feed_hidden_at"), rs.getTimestamp("feed_deleted_at"))) {
            return null;
        }
        return new ReportTargetLookup(ReportTargetType.COMMENT, targetId, rs.getLong("author_id"), rs.getString("content"));
    }

    /**
     * 다른 회원에게는 목록과 같이 PUBLIC만 보여 줍니다.
     * 작성자는 자신의 비공개 글도 조회해, 접수 서비스가 자기 신고를 거절할 수 있게 합니다.
     */
    private static boolean visibleTo(long reporterId, long authorId, String visibility, Timestamp hiddenAt, Timestamp deletedAt) {
        if (hiddenAt != null || deletedAt != null || visibilityOf(visibility) == null) {
            return false;
        }
        if (reporterId == authorId) {
            return true;
        }
        return Visibility.PUBLIC.name().equals(visibility);
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
