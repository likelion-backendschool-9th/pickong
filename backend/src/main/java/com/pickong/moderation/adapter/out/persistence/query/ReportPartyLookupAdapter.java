package com.pickong.moderation.adapter.out.persistence.query;

import com.pickong.moderation.application.dto.ReportPartyView;
import com.pickong.moderation.application.port.out.ReportPartyLookupPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 신고 화면에 필요한 아이디 · 이름 · 프로필 사진만 읽습니다.
 * user 모듈의 저장소와 엔티티는 주입하지 않고, 이메일 등 다른 컬럼은 조회하지 않습니다.
 */
@Component
public class ReportPartyLookupAdapter implements ReportPartyLookupPort {

    private final JdbcTemplate jdbc;

    public ReportPartyLookupAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, ReportPartyView> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT id, username, display_name, profile_image_url
                  FROM users
                 WHERE id IN (%s)
                """.formatted(SqlIn.placeholders(userIds.size()));
        Map<Long, ReportPartyView> found = new HashMap<>();
        jdbc.query(sql, rs -> {
            String username = rs.getString("username");
            String name = rs.getString("display_name");
            String nickname = name == null || name.isBlank() ? username : name;
            found.put(rs.getLong("id"), new ReportPartyView(
                    rs.getLong("id"), username, nickname, rs.getString("profile_image_url")));
        }, userIds.toArray());
        return found;
    }
}
