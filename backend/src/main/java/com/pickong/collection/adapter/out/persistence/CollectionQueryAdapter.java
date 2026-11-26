package com.pickong.collection.adapter.out.persistence;

import com.pickong.collection.application.dto.CollectionSummary;
import com.pickong.collection.application.port.out.CollectionQueryPort;
import com.pickong.common.domain.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 컬렉션 카드 목록을 읽습니다. 표지 계산에 places를 읽기만 하고, place 모듈의 저장소와 엔티티는 주입하지 않습니다.
 */
@Component
public class CollectionQueryAdapter implements CollectionQueryPort {

    /**
     * 표지를 따로 정하지 않았으면 순서상 첫 번째로 사진이 있는 장소의 사진을 씁니다 (mock과 같은 규칙).
     * 삭제된 장소와, 남이 직접 추가한 비공개 장소의 사진은 쓰지 않습니다.
     */
    private static final String SELECT_BY_OWNER = """
            SELECT c.public_id, c.title, c.description, c.visibility, c.pin_style, c.place_count, c.price,
                   COALESCE(c.cover_image_url, (
                       SELECT p.image_url
                         FROM collection_places cp
                         JOIN places p ON p.id = cp.place_id
                        WHERE cp.collection_id = c.id
                          AND p.deleted_at IS NULL
                          AND p.image_url IS NOT NULL
                          AND (p.visibility = 'PUBLIC' OR p.owner_id = :viewerId)
                        ORDER BY cp.sort_order, cp.id
                        LIMIT 1)) AS cover_image_url
              FROM collections c
             WHERE c.user_id = :ownerId
               AND c.deleted_at IS NULL
               AND c.hidden_at IS NULL
               AND c.visibility IN (:visibilities)
             ORDER BY c.created_at DESC, c.id DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CollectionQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CollectionSummary> findByOwner(long ownerId, Set<Visibility> visibilities, long viewerId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("viewerId", viewerId)
                .addValue("visibilities", visibilities.stream().map(Visibility::name).toList());
        return jdbc.query(SELECT_BY_OWNER, params, (rs, rowNum) -> toSummary(rs));
    }

    private static CollectionSummary toSummary(ResultSet rs) throws SQLException {
        return new CollectionSummary(
                rs.getObject("public_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("cover_image_url"),
                Visibility.valueOf(rs.getString("visibility")),
                rs.getString("pin_style"),
                rs.getInt("place_count"),
                rs.getObject("price", Integer.class));
    }
}
