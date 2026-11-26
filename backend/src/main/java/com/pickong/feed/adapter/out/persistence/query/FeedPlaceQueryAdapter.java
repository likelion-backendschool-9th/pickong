package com.pickong.feed.adapter.out.persistence.query;

import com.pickong.feed.application.dto.FeedPlaceSummary;
import com.pickong.feed.application.port.out.FeedPlaceQueryPort;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 피드 지도용 장소 조회 어댑터.
 * places(place 모듈) · follows(social 모듈) 테이블을 JOIN하는 읽기 전용 SQL.
 * blueprint 6.1: 다른 모듈 테이블 JOIN은 adapter/out/persistence/query/ 에만 허용.
 * JPA 엔티티·리포지토리를 쓰지 않고 SQL만 사용.
 */
@Repository
public class FeedPlaceQueryAdapter implements FeedPlaceQueryPort {

    private static final String RECOMMENDED_SQL = """
            SELECT
                p.id              AS place_id,
                p.public_id::text AS place_public_id,
                p.name            AS place_name,
                p.address         AS place_address,
                ST_Y(p.location::geometry) AS lat,
                ST_X(p.location::geometry) AS lng,
                p.category        AS category,
                p.source_type     AS source_type,
                f.id              AS feed_id
            FROM feeds f
            JOIN feed_places fp ON fp.feed_id = f.id
            JOIN places p       ON p.id = fp.place_id
            WHERE f.deleted_at IS NULL
              AND f.hidden_at IS NULL
              AND f.visibility = 'PUBLIC'
              AND p.deleted_at IS NULL
              AND p.visibility = 'PUBLIC'
            ORDER BY f.created_at DESC
            LIMIT 500
            """;

    private static final String FOLLOWING_SQL = """
            SELECT
                p.id              AS place_id,
                p.public_id::text AS place_public_id,
                p.name            AS place_name,
                p.address         AS place_address,
                ST_Y(p.location::geometry) AS lat,
                ST_X(p.location::geometry) AS lng,
                p.category        AS category,
                p.source_type     AS source_type,
                f.id              AS feed_id
            FROM feeds f
            JOIN feed_places fp ON fp.feed_id = f.id
            JOIN places p       ON p.id = fp.place_id
            WHERE f.deleted_at IS NULL
              AND f.hidden_at IS NULL
              AND f.visibility = 'PUBLIC'
              AND p.deleted_at IS NULL
              AND p.visibility = 'PUBLIC'
              AND f.user_id IN (
                  SELECT fw.followee_id FROM follows fw WHERE fw.follower_id = :userId
              )
            ORDER BY f.created_at DESC
            LIMIT 500
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public FeedPlaceQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FeedPlaceSummary> findRecommendedFeedPlaces() {
        return jdbc.query(RECOMMENDED_SQL, new MapSqlParameterSource(), rowMapper())
                .stream().filter(this::hasValidCoordinate).toList();
    }

    @Override
    public List<FeedPlaceSummary> findFollowingFeedPlaces(Long userId) {
        return jdbc.query(FOLLOWING_SQL, new MapSqlParameterSource("userId", userId), rowMapper())
                .stream().filter(this::hasValidCoordinate).toList();
    }

    // 좌표가 null(0.0으로 반환)인 행은 지도에 (0,0) 마커가 찍이므로 제외
    private boolean hasValidCoordinate(FeedPlaceSummary row) {
        return row.getLat() != 0.0 || row.getLng() != 0.0;
    }

    private RowMapper<FeedPlaceSummary> rowMapper() {
        return (rs, rowNum) -> new FeedPlaceSummary(
                rs.getLong("place_id"),
                rs.getString("place_public_id"),
                rs.getString("place_name"),
                rs.getString("place_address"),
                rs.getDouble("lat"),
                rs.getDouble("lng"),
                rs.getString("category"),
                rs.getString("source_type"),
                rs.getLong("feed_id")
        );
    }
}
