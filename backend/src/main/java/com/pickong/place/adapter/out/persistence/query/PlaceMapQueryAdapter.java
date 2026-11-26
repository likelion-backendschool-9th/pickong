package com.pickong.place.adapter.out.persistence.query;

import com.pickong.place.application.dto.PlaceCluster;
import com.pickong.place.application.dto.PlaceMapGroup;
import com.pickong.place.application.dto.PlaceMapItem;
import com.pickong.place.application.dto.PlaceMapQuery;
import com.pickong.place.application.port.out.PlaceMapQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 지도 범위 안의 장소를 좌표 · 격자로 묶어 읽습니다. 값은 항상 바인딩합니다. */
@Component
public class PlaceMapQueryAdapter implements PlaceMapQueryPort {

    /**
     * 범위 조건. &&는 float 정밀도의 사각형 비교라 경계 밖 장소가 섞일 수 있어 좌표를 한 번 더 비교합니다.
     */
    private static final String WHERE_IN_BOUNDS = """
             WHERE p.visibility = 'PUBLIC' AND p.deleted_at IS NULL
               AND p.location::geometry && ST_MakeEnvelope(?, ?, ?, ?, 4326)
               AND ST_X(p.location::geometry) BETWEEN ? AND ?
               AND ST_Y(p.location::geometry) BETWEEN ? AND ?
               AND (CAST(? AS varchar) IS NULL OR p.category = ?)
            """;

    /** 묶는 동안에는 좌표만 다룹니다. 장소 값은 혼자 있는 장소만 나중에 읽습니다. */
    private static final String SPOTS = """
            SELECT p.id, ST_X(p.location::geometry) AS lng, ST_Y(p.location::geometry) AS lat
              FROM places p
            """ + WHERE_IN_BOUNDS;

    private static final String GROUP_COLUMNS = """
            SELECT count(*) AS cnt, avg(lng) AS lng, avg(lat) AS lat,
                   min(lng) AS west, min(lat) AS south, max(lng) AS east, max(lat) AS north, min(id) AS first_id
              FROM spots
            """;

    private static final String SELECT_GROUPS = """
            SELECT g.cnt, g.lng, g.lat, g.west, g.south, g.east, g.north,
                   p.public_id, p.name, p.address, p.category, p.source_type
              FROM groups g LEFT JOIN places p ON g.cnt = 1 AND p.id = g.first_id
             ORDER BY g.first_id
            """;

    private static final String BY_SPOT = "WITH spots AS (" + SPOTS + "), groups AS ("
            + GROUP_COLUMNS + " GROUP BY lng, lat LIMIT ?) " + SELECT_GROUPS;

    private static final String BY_GRID = "WITH spots AS (" + SPOTS + "), groups AS ("
            + GROUP_COLUMNS + " GROUP BY floor(lng / ?), floor(degrees(ln(tan(radians(45 + lat / 2)))) / ?)) "
            + SELECT_GROUPS;

    private static final String LIST = """
            SELECT p.public_id, p.name, p.address, p.category, p.source_type,
                   ST_X(p.location::geometry) AS lng, ST_Y(p.location::geometry) AS lat
              FROM places p
            """ + WHERE_IN_BOUNDS + " ORDER BY p.name, p.id LIMIT ?";

    private final JdbcTemplate jdbc;

    public PlaceMapQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PlaceMapGroup> groupBySpot(PlaceMapQuery query, int limit) {
        return jdbc.query(BY_SPOT, (rs, rowNum) -> toGroup(rs), arguments(query, limit));
    }

    @Override
    public List<PlaceMapGroup> groupByGrid(PlaceMapQuery query, double cellSize) {
        return jdbc.query(BY_GRID, (rs, rowNum) -> toGroup(rs), arguments(query, cellSize, cellSize));
    }

    @Override
    public List<PlaceMapItem> list(PlaceMapQuery query, int limit) {
        return jdbc.query(LIST, (rs, rowNum) -> toItem(rs), arguments(query, limit));
    }

    private static Object[] arguments(PlaceMapQuery query, Object... rest) {
        List<Object> values = new ArrayList<>(List.of(
                query.west(), query.south(), query.east(), query.north(),
                query.west(), query.east(), query.south(), query.north()));
        values.add(query.category());
        values.add(query.category());
        values.addAll(List.of(rest));
        return values.toArray();
    }

    private static PlaceMapGroup toGroup(ResultSet rs) throws SQLException {
        PlaceCluster cluster = new PlaceCluster(
                rs.getDouble("lat"),
                rs.getDouble("lng"),
                rs.getLong("cnt"),
                rs.getDouble("west"),
                rs.getDouble("south"),
                rs.getDouble("east"),
                rs.getDouble("north"));
        return new PlaceMapGroup(cluster, cluster.count() == 1 ? toItem(rs) : null);
    }

    private static PlaceMapItem toItem(ResultSet rs) throws SQLException {
        return new PlaceMapItem(
                rs.getObject("public_id", UUID.class),
                rs.getString("name"),
                rs.getString("address"),
                rs.getDouble("lat"),
                rs.getDouble("lng"),
                rs.getString("category"),
                rs.getString("source_type"));
    }
}
