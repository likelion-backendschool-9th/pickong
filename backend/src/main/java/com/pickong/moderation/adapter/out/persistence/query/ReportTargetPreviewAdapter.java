package com.pickong.moderation.adapter.out.persistence.query;

import com.pickong.moderation.application.dto.ReportContent;
import com.pickong.moderation.application.dto.ReportTargetKey;
import com.pickong.moderation.application.dto.ReportTargetPreview;
import com.pickong.moderation.application.dto.ReportTargetVisibility;
import com.pickong.moderation.application.port.out.ReportTargetPreviewPort;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.sql.Timestamp;
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
 * 현재 콘텐츠는 대상마다 한 번만 읽고, 미리보기는 신고 id로 나눠 서로의 스냅샷을 덮지 않습니다.
 * 저장된 스냅샷은 현재 글이 있어도 그대로 두고, 현재 글을 스냅샷에 복사하지 않습니다.
 * 숨김 콘텐츠는 현재 글을 보여주고 링크는 비웁니다. 삭제된 행은 현재 글 없이 DELETED입니다.
 * 행이 없거나 컬렉션처럼 조회할 수 없으면 UNAVAILABLE이며 삭제로 단정하지 않습니다.
 */
@Component
public class ReportTargetPreviewAdapter implements ReportTargetPreviewPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    public ReportTargetPreviewAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, ReportTargetPreview> find(List<Report> reports) {
        if (reports == null || reports.isEmpty()) {
            return Map.of();
        }
        Set<Long> feedIds = ids(reports, ReportTargetType.FEED);
        Set<Long> commentIds = ids(reports, ReportTargetType.COMMENT);
        Set<Long> userIds = ids(reports, ReportTargetType.USER);
        Map<ReportTargetKey, LiveTarget> live = new HashMap<>();
        liveFeeds(feedIds).forEach((id, target) -> live.put(new ReportTargetKey(ReportTargetType.FEED, id), target));
        liveComments(commentIds).forEach((id, target) -> live.put(new ReportTargetKey(ReportTargetType.COMMENT, id), target));
        liveUsers(userIds).forEach((id, target) -> live.put(new ReportTargetKey(ReportTargetType.USER, id), target));

        Map<Long, ReportTargetPreview> previews = new LinkedHashMap<>();
        for (Report report : reports) {
            if (report.getId() == null) {
                continue;
            }
            ReportTargetKey target = new ReportTargetKey(report.getTargetType(), report.getTargetId());
            previews.put(report.getId(), preview(report, live.get(target)));
        }
        return previews;
    }

    private static ReportTargetPreview preview(Report report, LiveTarget live) {
        Snapshot snapshot = readSnapshot(report.getSnapshot());
        boolean stored = report.getSnapshot() != null && !report.getSnapshot().isBlank();
        ReportContent snapshotContent = stored ? new ReportContent(snapshot.text(), snapshot.imageUrl()) : null;
        ReportTargetVisibility visibility = live == null ? ReportTargetVisibility.UNAVAILABLE : live.visibility();
        boolean readable = visibility == ReportTargetVisibility.LIVE || visibility == ReportTargetVisibility.HIDDEN;
        ReportContent currentContent = readable ? new ReportContent(live.excerpt(), null) : null;
        String evidence = excerpt(snapshot.text());
        String text = evidence != null ? evidence : fallbackText(report.getTargetType(), visibility, live);
        String imageUrl = snapshotContent == null ? null : snapshotContent.imageUrl();
        String link = visibility == ReportTargetVisibility.LIVE ? live.link() : null;
        Long reportedUserId = live != null ? Long.valueOf(live.reportedUserId()) : snapshot.reportedUserId();
        return new ReportTargetPreview(
                text, imageUrl, link, reportedUserId, snapshotContent, currentContent,
                readable ? modified(evidence, live) : null, visibility);
    }

    private static String fallbackText(ReportTargetType type, ReportTargetVisibility visibility, LiveTarget live) {
        return switch (visibility) {
            case LIVE, HIDDEN -> live.text();
            case DELETED -> deletedText(type);
            case UNAVAILABLE -> unavailableText(type);
        };
    }

    /** 스냅샷 첫 줄과 현재 첫 줄이 둘 다 있을 때만 수정 여부를 판단합니다. */
    private static Boolean modified(String snapshotLine, LiveTarget live) {
        if (snapshotLine == null || live == null || live.excerpt() == null) {
            return null;
        }
        return !snapshotLine.equals(live.excerpt());
    }

    private Map<Long, LiveTarget> liveFeeds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT id, public_id::text AS public_id, user_id, content, hidden_at, deleted_at
                  FROM feeds
                 WHERE id IN (%s)
                """.formatted(SqlIn.placeholders(ids.size()));
        Map<Long, LiveTarget> found = new HashMap<>();
        jdbc.query(sql, rs -> {
            String publicId = rs.getString("public_id");
            found.put(rs.getLong("id"), contentTarget(
                    rs.getString("content"),
                    "게시물",
                    publicId == null ? null : "/posts/" + publicId,
                    rs.getLong("user_id"),
                    rs.getTimestamp("hidden_at"),
                    rs.getTimestamp("deleted_at")));
        }, ids.toArray());
        return found;
    }

    private Map<Long, LiveTarget> liveComments(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT c.id, c.user_id, c.content, c.hidden_at, c.deleted_at,
                       f.public_id::text AS feed_public_id, f.deleted_at AS feed_deleted_at
                  FROM comments c
                  LEFT JOIN feeds f ON f.id = c.feed_id
                 WHERE c.id IN (%s)
                """.formatted(SqlIn.placeholders(ids.size()));
        Map<Long, LiveTarget> found = new HashMap<>();
        jdbc.query(sql, rs -> {
            String feedPublicId = rs.getString("feed_public_id");
            boolean feedGone = feedPublicId == null || rs.getTimestamp("feed_deleted_at") != null;
            found.put(rs.getLong("id"), contentTarget(
                    rs.getString("content"),
                    "댓글",
                    feedGone ? null : "/posts/" + feedPublicId,
                    rs.getLong("user_id"),
                    rs.getTimestamp("hidden_at"),
                    rs.getTimestamp("deleted_at")));
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
                found.put(rs.getLong("id"), new LiveTarget(null, null, null, rs.getLong("id"), ReportTargetVisibility.DELETED));
                return;
            }
            String username = rs.getString("username");
            String name = rs.getString("display_name");
            String nickname = name == null || name.isBlank() ? username : name;
            String label = nickname + " (@" + username + ")";
            found.put(rs.getLong("id"), new LiveTarget(
                    label, label, "/users/" + username, rs.getLong("id"), ReportTargetVisibility.LIVE));
        }, ids.toArray());
        return found;
    }

    private static Set<Long> ids(List<Report> reports, ReportTargetType type) {
        return reports.stream()
                .filter(report -> report.getTargetType() == type)
                .map(Report::getTargetId)
                .collect(Collectors.toSet());
    }

    private static LiveTarget contentTarget(String raw, String blankLabel, String link, long userId,
            Timestamp hiddenAt, Timestamp deletedAt) {
        if (deletedAt != null) {
            return new LiveTarget(null, null, null, userId, ReportTargetVisibility.DELETED);
        }
        String line = excerpt(raw);
        boolean hidden = hiddenAt != null;
        return new LiveTarget(
                line == null ? blankLabel : line,
                line,
                hidden ? null : link,
                userId,
                hidden ? ReportTargetVisibility.HIDDEN : ReportTargetVisibility.LIVE);
    }

    /** 비교와 카드 제목에 쓰는 첫 줄. 내용이 없으면 null입니다. */
    private static String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        int newline = content.indexOf('\n');
        String line = newline < 0 ? content : content.substring(0, newline);
        String trimmed = line.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String deletedText(ReportTargetType type) {
        return switch (type) {
            case FEED -> "삭제된 게시물";
            case COMMENT -> "삭제된 댓글";
            case USER -> "탈퇴한 사용자";
            case COLLECTION -> "확인할 수 없는 컬렉션";
        };
    }

    private static String unavailableText(ReportTargetType type) {
        return switch (type) {
            case FEED -> "확인할 수 없는 게시물";
            case COMMENT -> "확인할 수 없는 댓글";
            case USER -> "확인할 수 없는 사용자";
            case COLLECTION -> "확인할 수 없는 컬렉션";
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

    private record LiveTarget(
            String text, String excerpt, String link, long reportedUserId, ReportTargetVisibility visibility) {
    }

    private record Snapshot(String text, String imageUrl, Long reportedUserId) {
        private static final Snapshot EMPTY = new Snapshot(null, null, null);
    }
}
