package com.pickong.moderation.adapter.out.persistence.query;

import com.pickong.moderation.application.dto.AdminUserView;
import com.pickong.moderation.application.dto.AdminUserView.ActiveSanction;
import com.pickong.moderation.application.port.out.AdminUserQueryPort;
import com.pickong.moderation.domain.vo.SanctionType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 청사진 6.1의 조회 SQL JOIN 예외. 다른 모듈 테이블은 읽기만 하며 Entity·Repository를 참조하지 않습니다.
 * 후보 사용자, 신고 작성자, 유효 제재를 각각 한 번에 읽어 사용자별 반복 조회를 피합니다.
 * 관리자 조회이므로 숨김 콘텐츠의 신고도 세고, 게시물 수는 삭제되지 않은 글(숨김 포함)입니다.
 */
@Component
public class AdminUserQueryAdapter implements AdminUserQueryPort {
    private final JdbcTemplate jdbc;

    public AdminUserQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AdminUserView> findUsers(String query, OffsetDateTime now) {
        List<BaseUser> candidates = jdbc.query("""
                SELECT u.id, u.username, u.display_name, u.profile_image_url, u.status,
                       COALESCE(f.post_count, 0) AS post_count
                  FROM users u
                  LEFT JOIN (SELECT user_id, count(*) AS post_count FROM feeds
                              WHERE deleted_at IS NULL GROUP BY user_id) f ON f.user_id = u.id
                 WHERE strpos(lower(COALESCE(u.display_name, '') || u.username), lower(?)) > 0
                """, (rs, row) -> new BaseUser(rs.getLong("id"), rs.getString("username"),
                rs.getString("display_name"), rs.getString("profile_image_url"), rs.getString("status"),
                rs.getLong("post_count")), query);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> reportCounts = reportCounts();
        Map<Long, List<ActiveSanction>> sanctions = activeSanctions(query, now);
        return candidates.stream().map(user -> new AdminUserView(user.id(), user.username(),
                user.name() == null || user.name().isBlank() ? user.username() : user.name(), user.imageUrl(),
                reportCounts.getOrDefault(user.id(), 0L), user.postCount(), user.status(),
                sanctions.getOrDefault(user.id(), List.of()))).toList();
    }

    /**
     * 처리 상태와 관계없이 신고 행마다 한 번 셉니다. 콘텐츠가 있으면 실제 작성자를 우선합니다.
     * 물리적으로 없는 대상은 기존 신고 미리보기와 같은 스냅샷 파서로 작성자를 복원하며,
     * 알 수 없는 작성자를 target_id 또는 신고자 id로 대체하지 않습니다.
     */
    private Map<Long, Long> reportCounts() {
        Map<Long, Long> counts = new HashMap<>();
        jdbc.query("""
                SELECT CASE r.target_type WHEN 'USER' THEN u.id WHEN 'FEED' THEN f.user_id
                            WHEN 'COMMENT' THEN c.user_id END AS author_id,
                       CASE WHEN COALESCE(u.id, f.user_id, c.user_id) IS NULL THEN r.snapshot END AS snapshot
                  FROM reports r
                  LEFT JOIN users u ON r.target_type = 'USER' AND u.id = r.target_id
                  LEFT JOIN feeds f ON r.target_type = 'FEED' AND f.id = r.target_id
                  LEFT JOIN comments c ON r.target_type = 'COMMENT' AND c.id = r.target_id
                """, rs -> {
            Long authorId = rs.getObject("author_id", Long.class);
            if (authorId == null) {
                authorId = ReportTargetPreviewAdapter.snapshotReportedUserId(rs.getString("snapshot"));
            }
            if (authorId != null && authorId > 0) {
                counts.merge(authorId, 1L, Long::sum);
            }
        });
        return counts;
    }

    private Map<Long, List<ActiveSanction>> activeSanctions(String query, OffsetDateTime now) {
        Map<Long, List<ActiveSanction>> found = new HashMap<>();
        jdbc.query("""
                SELECT s.id, s.user_id, s.type, s.reason, s.created_at, s.suspended_until
                  FROM user_sanctions s JOIN users u ON u.id = s.user_id
                 WHERE s.ended_at IS NULL AND s.created_at <= ?
                   AND (s.type <> 'SUSPEND' OR s.suspended_until > ?)
                   AND strpos(lower(COALESCE(u.display_name, '') || u.username), lower(?)) > 0
                 ORDER BY s.user_id, CASE WHEN s.type = 'WARNING' THEN 1 ELSE 0 END,
                          s.created_at DESC, s.id DESC
                """, rs -> {
            ActiveSanction sanction = new ActiveSanction(rs.getLong("id"), SanctionType.valueOf(rs.getString("type")),
                    rs.getString("reason"), rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("suspended_until", OffsetDateTime.class));
            found.computeIfAbsent(rs.getLong("user_id"), ignored -> new ArrayList<>()).add(sanction);
        }, now, now, query);
        return found;
    }

    private record BaseUser(long id, String username, String name, String imageUrl, String status, long postCount) {
    }
}
