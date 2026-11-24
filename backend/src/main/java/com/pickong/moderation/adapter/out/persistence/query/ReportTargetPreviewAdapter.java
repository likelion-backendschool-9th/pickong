package com.pickong.moderation.adapter.out.persistence.query;

import com.pickong.moderation.application.dto.ReportTargetKey;
import com.pickong.moderation.application.dto.ReportTargetPreview;
import com.pickong.moderation.application.port.out.ReportTargetPreviewPort;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 신고 대상 미리보기. feed · user 테이블은 읽기만 하고, 컬렉션 테이블은 아직 없어 조회하지 않습니다.
 * 대상이 없거나 삭제됐으면 스냅샷을 쓰고 이동 링크는 비웁니다.
 */
@Component
public class ReportTargetPreviewAdapter implements ReportTargetPreviewPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    public ReportTargetPreviewAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<ReportTargetKey, ReportTargetPreview> find(List<Report> reports) {
        if (reports == null || reports.isEmpty()) {
            return Map.of();
        }
        Set<Long> feedIds = ids(reports, ReportTargetType.FEED);
        Set<Long> commentIds = ids(reports, ReportTargetType.COMMENT);
        Set<Long> userIds = ids(reports, ReportTargetType.USER);
        Map<Long, LiveTarget> feeds = liveFeeds(feedIds);
        Map<Long, LiveTarget> comments = liveComments(commentIds);
        Map<Long, LiveTarget> users = liveUsers(userIds);

        Map<ReportTargetKey, ReportTargetPreview> previews = new LinkedHashMap<>();
        for (Report report : reports) {
            ReportTargetKey key = new ReportTargetKey(report.getTargetType(), report.getTargetId());
            previews.put(key, preview(report, liveOf(report, feeds, comments, users)));
        }
        return previews;
    }

    private static LiveTarget liveOf(Report report, Map<Long, LiveTarget> feeds, Map<Long, LiveTarget> comments,
            Map<Long, LiveTarget> users) {
        return switch (report.getTargetType()) {
            case FEED -> feeds.get(report.getTargetId());
            case COMMENT -> comments.get(report.getTargetId());
            case USER -> users.get(report.getTargetId());
            case COLLECTION -> null;
        };
    }

    private static ReportTargetPreview preview(Report report, LiveTarget live) {
        Snapshot snapshot = readSnapshot(report.getSnapshot());
        if (live != null) {
            return new ReportTargetPreview(live.text(), null, live.link(), live.reportedUserId());
        }
        String text = snapshot.text();
        if (text == null || text.isBlank()) {
            text = missingText(report.getTargetType());
        }
        return new ReportTargetPreview(text, snapshot.imageUrl(), null, snapshot.reportedUserId());
    }

    private Map<Long, LiveTarget> liveFeeds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT id, public_id::text AS public_id, user_id, content, deleted_at
                  FROM feeds
                 WHERE id IN (%s)
                """.formatted(SqlIn.placeholders(ids.size()));
        Map<Long, LiveTarget> found = new HashMap<>();
        jdbc.query(sql, rs -> {
            if (rs.getTimestamp("deleted_at") != null) {
                return;
            }
            String publicId = rs.getString("public_id");
            found.put(rs.getLong("id"), new LiveTarget(
                    firstLine(rs.getString("content"), "게시물"),
                    publicId == null ? null : "/posts/" + publicId,
                    rs.getLong("user_id")));
        }, ids.toArray());
        return found;
    }

    private Map<Long, LiveTarget> liveComments(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT c.id, c.user_id, c.content, c.deleted_at,
                       f.public_id::text AS feed_public_id, f.deleted_at AS feed_deleted_at
                  FROM comments c
                  LEFT JOIN feeds f ON f.id = c.feed_id
                 WHERE c.id IN (%s)
                """.formatted(SqlIn.placeholders(ids.size()));
        Map<Long, LiveTarget> found = new HashMap<>();
        jdbc.query(sql, rs -> {
            if (rs.getTimestamp("deleted_at") != null) {
                return;
            }
            String feedPublicId = rs.getString("feed_public_id");
            boolean feedGone = feedPublicId == null || rs.getTimestamp("feed_deleted_at") != null;
            found.put(rs.getLong("id"), new LiveTarget(
                    firstLine(rs.getString("content"), "댓글"),
                    feedGone ? null : "/posts/" + feedPublicId,
                    rs.getLong("user_id")));
        }, ids.toArray());
        return found;
    }

    private Map<Long, LiveTarget> liveUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT id, username, display_name, profile_image_url, deleted_at
                  FROM users
                 WHERE id IN (%s)
                """.formatted(SqlIn.placeholders(ids.size()));
        Map<Long, LiveTarget> found = new HashMap<>();
        jdbc.query(sql, rs -> {
            if (rs.getTimestamp("deleted_at") != null) {
                return;
            }
            String username = rs.getString("username");
            String name = rs.getString("display_name");
            String nickname = name == null || name.isBlank() ? username : name;
            found.put(rs.getLong("id"), new LiveTarget(
                    nickname + " (@" + username + ")",
                    "/users/" + username,
                    rs.getLong("id")));
        }, ids.toArray());
        return found;
    }

    private static Set<Long> ids(List<Report> reports, ReportTargetType type) {
        return reports.stream()
                .filter(report -> report.getTargetType() == type)
                .map(Report::getTargetId)
                .collect(Collectors.toSet());
    }

    private static String firstLine(String content, String fallback) {
        if (content == null || content.isBlank()) {
            return fallback;
        }
        int newline = content.indexOf('\n');
        String line = newline < 0 ? content : content.substring(0, newline);
        String trimmed = line.strip();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String missingText(ReportTargetType type) {
        return switch (type) {
            case FEED -> "삭제된 게시물";
            case COMMENT -> "삭제된 댓글";
            case USER -> "탈퇴한 사용자";
            case COLLECTION -> "삭제된 컬렉션";
        };
    }

    private static Snapshot readSnapshot(String raw) {
        if (raw == null || raw.isBlank()) {
            return Snapshot.EMPTY;
        }
        String trimmed = raw.strip();
        if (!trimmed.startsWith("{")) {
            return new Snapshot(trimmed, null, null);
        }
        try {
            JsonNode node = JSON.readTree(trimmed);
            String text = text(node.get("text"));
            String imageUrl = text(node.get("imageUrl"));
            JsonNode reportedUserId = node.get("reportedUserId");
            Long userId = reportedUserId == null || reportedUserId.isNull() ? null : reportedUserId.asLong();
            return new Snapshot(text, imageUrl, userId);
        } catch (RuntimeException exception) {
            return new Snapshot(trimmed, null, null);
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return value == null || value.isBlank() ? null : value;
    }

    private record LiveTarget(String text, String link, long reportedUserId) {
    }

    private record Snapshot(String text, String imageUrl, Long reportedUserId) {
        private static final Snapshot EMPTY = new Snapshot(null, null, null);
    }
}
