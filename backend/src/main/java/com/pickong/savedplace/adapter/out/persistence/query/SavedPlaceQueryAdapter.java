package com.pickong.savedplace.adapter.out.persistence.query;

import com.pickong.savedplace.application.dto.SavedPlaceInfo;
import com.pickong.savedplace.application.dto.SavedPlaceResult;
import com.pickong.savedplace.application.port.out.SavedPlaceQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 내 저장 장소와 장소 표시 정보를 한 번에 읽습니다. places는 읽기만 합니다.
 * place 모듈의 저장소와 엔티티는 주입하지 않습니다.
 */
@Component
public class SavedPlaceQueryAdapter implements SavedPlaceQueryPort {

    /** 삭제된 장소와, 다른 회원이 비공개로 바꾼 장소는 읽지 않습니다. */
    private static final String SELECT_MINE = """
            SELECT s.id, s.visit_status, s.memo, s.source, s.created_at,
                   p.public_id, p.name, p.address, p.region,
                   ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lng,
                   p.category, p.source_type, p.visibility, p.description, p.image_url
              FROM saved_places s
              JOIN places p ON p.id = s.place_id
             WHERE s.user_id = ?
               AND p.deleted_at IS NULL
               AND (p.visibility = 'PUBLIC' OR p.owner_id = s.user_id)
             ORDER BY s.created_at DESC, s.id DESC
            """;

    private final JdbcTemplate jdbc;

    public SavedPlaceQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SavedPlaceResult> findByUserId(long userId) {
        return jdbc.query(SELECT_MINE, (rs, rowNum) -> toResult(rs), userId);
    }

    private static SavedPlaceResult toResult(ResultSet rs) throws SQLException {
        return new SavedPlaceResult(
                rs.getLong("id"),
                new SavedPlaceInfo(
                        rs.getObject("public_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("address"),
                        rs.getString("region"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getString("category"),
                        rs.getString("source_type"),
                        rs.getString("visibility"),
                        rs.getString("description"),
                        rs.getString("image_url")),
                rs.getString("visit_status"),
                rs.getString("memo"),
                rs.getString("source"),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
