package com.pickong.place.adapter.out.persistence;

import com.pickong.place.application.dto.SubmitterView;
import com.pickong.place.application.port.out.SubmitterLookupPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 제보자 표시에 필요한 회원 공개 id · 아이디 · 이름만 읽습니다.
 * user 모듈의 저장소와 엔티티는 주입하지 않습니다.
 */
@Component
public class SubmitterLookupAdapter implements SubmitterLookupPort {

    private final JdbcTemplate jdbc;

    public SubmitterLookupAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, SubmitterView> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT id, public_id, username, display_name
                FROM users
                WHERE id IN (%s)
                """.formatted(JdbcIn.placeholders(userIds.size()));
        Map<Long, SubmitterView> found = new HashMap<>();
        jdbc.query(sql, rs -> {
            String username = rs.getString("username");
            String name = rs.getString("display_name");
            String nickname = name == null || name.isBlank() ? username : name;
            found.put(rs.getLong("id"), new SubmitterView(JdbcIn.uuid(rs, "public_id").toString(), username, nickname));
        }, userIds.toArray());
        return found;
    }
}
