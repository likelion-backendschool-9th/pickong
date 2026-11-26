package com.pickong.feed.adapter.out.persistence.query;

import com.pickong.common.domain.Visibility;
import com.pickong.feed.application.dto.AdminFeedView;
import com.pickong.feed.application.dto.AdminFeedView.Author;
import com.pickong.feed.application.port.out.AdminFeedQueryPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 청사진 6.1의 조회 SQL JOIN 예외. 다른 모듈의 Entity·Repository를 참조하지 않습니다.
 * 신고를 대상별로 먼저 집계해 JOIN의 행 증식을 막고 작성자와 함께 한 번에 읽습니다.
 * 관리자 목록에는 숨김·비공개 글도 포함하며, 삭제된 글만 제외합니다.
 */
@Component
public class AdminFeedQueryAdapter implements AdminFeedQueryPort {
    private final JdbcTemplate jdbc;

    public AdminFeedQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AdminFeedView> findAll() {
        return find("", new Object[0]);
    }

    @Override
    public Optional<AdminFeedView> findByPublicId(UUID publicId) {
        return find(" AND f.public_id = ?", publicId).stream().findFirst();
    }

    private List<AdminFeedView> find(String predicate, Object... parameters) {
        return jdbc.query("""
                SELECT f.public_id,
                       split_part(replace(replace(COALESCE(f.content, ''), chr(13) || chr(10), chr(10)),
                                          chr(13), chr(10)), chr(10), 1) AS title,
                       f.visibility, f.hidden_at, f.created_at,
                       u.id AS author_id, u.username, u.display_name, u.profile_image_url,
                       COALESCE(r.report_count, 0) AS report_count
                  FROM feeds f
                  LEFT JOIN users u ON u.id = f.user_id
                  LEFT JOIN (SELECT target_id, count(*) AS report_count FROM reports
                              WHERE target_type = 'FEED' GROUP BY target_id) r ON r.target_id = f.id
                 WHERE f.deleted_at IS NULL
                """ + predicate + " ORDER BY f.created_at DESC, f.id DESC", (rs, row) -> {
            Long authorId = rs.getObject("author_id", Long.class);
            String username = rs.getString("username");
            String name = rs.getString("display_name");
            Author author = authorId == null ? null : new Author(authorId, username,
                    name == null || name.isBlank() ? username : name, rs.getString("profile_image_url"));
            return new AdminFeedView(rs.getObject("public_id", UUID.class), rs.getString("title"), author,
                    Visibility.valueOf(rs.getString("visibility")), rs.getTimestamp("hidden_at") != null,
                    rs.getLong("report_count"), rs.getObject("created_at", OffsetDateTime.class));
        }, parameters);
    }
}
