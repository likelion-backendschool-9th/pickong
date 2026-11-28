package com.pickong.savedplace.adapter.out.persistence.query;

import com.pickong.savedplace.application.dto.SavedPlaceInfo;
import com.pickong.savedplace.application.dto.SavedPlaceResult;
import com.pickong.savedplace.application.port.out.SavedPlaceQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 내 저장 장소와 장소 표시 정보를 한 번에 읽습니다. places · feeds는 읽기만 합니다 (backend_blueprint.md 6.1 조회 예외).
 * place · feed 모듈의 저장소와 엔티티는 주입하지 않습니다.
 */
@Component
public class SavedPlaceQueryAdapter implements SavedPlaceQueryPort {

    /**
     * 삭제된 장소와, 다른 회원이 비공개로 바꾼 장소는 읽지 않습니다.
     * 출처 게시물 · 컬렉션은 삭제 · 숨김이면 공개 id를 내보내지 않습니다(저장 기록은 그대로).
     */
    private static final String SELECT_MINE = """
            SELECT s.id, s.visit_status, s.memo, s.source_type AS saved_source, s.source_label, s.source_url,
                   s.created_at, f.public_id::text AS feed_public_id, c.public_id::text AS collection_public_id,
                   p.public_id, p.name, p.address, p.region,
                   ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lng,
                   p.category, p.source_type AS place_source, p.visibility, p.description, p.image_url
              FROM saved_places s
              JOIN places p ON p.id = s.place_id
              LEFT JOIN feeds f ON f.id = s.source_feed_id AND f.deleted_at IS NULL AND f.hidden_at IS NULL
              LEFT JOIN collections c ON c.id = s.source_collection_id AND c.deleted_at IS NULL AND c.hidden_at IS NULL
             WHERE s.user_id = :userId
               AND p.deleted_at IS NULL
               AND (p.visibility = 'PUBLIC' OR p.owner_id = s.user_id)
            """;

    private static final String ORDER = " ORDER BY s.created_at DESC, s.id DESC";

    private final NamedParameterJdbcTemplate jdbc;

    public SavedPlaceQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SavedPlaceResult> findByUserId(long userId) {
        return jdbc.query(SELECT_MINE + ORDER, new MapSqlParameterSource("userId", userId), (rs, rowNum) -> toResult(rs));
    }

    @Override
    public List<SavedPlaceResult> findByUserIdAndIds(long userId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("ids", ids);
        return jdbc.query(SELECT_MINE + " AND s.id IN (:ids)" + ORDER, params, (rs, rowNum) -> toResult(rs));
    }

    private static SavedPlaceResult toResult(ResultSet rs) throws SQLException {
        String source = rs.getString("saved_source");
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
                        rs.getString("place_source"),
                        rs.getString("visibility"),
                        rs.getString("description"),
                        rs.getString("image_url")),
                rs.getString("visit_status"),
                rs.getString("memo"),
                source,
                refId(source, rs.getString("feed_public_id"), rs.getString("collection_public_id"), rs.getString("source_url")),
                rs.getString("source_label"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    /** 게시물 출처는 게시물 공개 id, 컬렉션 출처는 컬렉션 공개 id, 링크 가져오기는 원문 URL입니다. */
    private static String refId(String source, String feedPublicId, String collectionPublicId, String sourceUrl) {
        return switch (source) {
            case "FEED" -> feedPublicId;
            case "COLLECTION" -> collectionPublicId;
            case "LINK_IMPORT" -> sourceUrl;
            case null, default -> null;
        };
    }
}
