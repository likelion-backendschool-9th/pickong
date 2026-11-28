package com.pickong.feed.adapter.out.persistence.query;

import com.pickong.feed.application.dto.FeedAuthor;
import com.pickong.feed.application.dto.FeedViewPlace;
import com.pickong.feed.application.port.out.FeedViewQueryPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 화면용 게시물 정보를 읽는 SQL. users(user) · media_files(media) · places(place) 테이블을 읽음
 * blueprint 6.1: 다른 모듈 테이블은 adapter/out/persistence/query/ 의 SQL로만 읽고 JPA 엔티티는 쓰지 않음
 * 모든 메서드는 id 목록을 IN으로 받아 종류별로 한 번만 조회함 (목록에서 게시물마다 쿼리가 나가지 않게)
 */
@Repository
public class FeedViewQueryAdapter implements FeedViewQueryPort {

    private static final String AUTHORS_SQL = """
            SELECT u.id, u.public_id::text AS public_id, u.username, u.display_name, u.profile_image_url, u.region
            FROM users u
            WHERE u.id IN (:ids)
            """;

    // 확인(confirm)이 끝나 공개 주소가 저장된 사진만 (UPLOADED, 서버 처리 후 PROCESSED). 업로드 중(PENDING) · 실패(FAILED) 사진은 깨진 이미지로 보이므로 뺌
    private static final String PHOTOS_SQL = """
            SELECT fp.feed_id, mf.url
            FROM feed_photos fp
            JOIN media_files mf ON mf.id = fp.media_file_id
            WHERE fp.feed_id IN (:ids)
              AND mf.status IN ('UPLOADED', 'PROCESSED')
              AND mf.url IS NOT NULL
            ORDER BY fp.feed_id, fp.sort_order, fp.id
            """;

    // 삭제된 장소는 빼고, 비공개 장소는 그 장소의 주인이 볼 때만 보여 줌 (피드 지도 쿼리와 같은 기준 + 주인 예외)
    private static final String PLACES_SQL = """
            SELECT fpl.feed_id,
                   p.public_id::text AS public_id, p.name, p.address, p.region,
                   ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lng,
                   p.category, p.source_type, p.visibility, p.description
            FROM feed_places fpl
            JOIN places p ON p.id = fpl.place_id
            WHERE fpl.feed_id IN (:ids)
              AND p.deleted_at IS NULL
              AND (p.visibility = 'PUBLIC' OR p.owner_id = CAST(:viewerId AS BIGINT))
            ORDER BY fpl.feed_id, fpl.id
            """;

    private static final String LIKED_SQL =
            "SELECT feed_id FROM feed_likes WHERE user_id = :viewerId AND feed_id IN (:ids)";

    private static final String BOOKMARKED_SQL =
            "SELECT feed_id FROM feed_bookmarks WHERE user_id = :viewerId AND feed_id IN (:ids)";

    private final NamedParameterJdbcTemplate jdbc;

    public FeedViewQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, FeedAuthor> findAuthors(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, FeedAuthor> authors = new HashMap<>();
        jdbc.query(AUTHORS_SQL, new MapSqlParameterSource("ids", userIds), rs -> {
            authors.put(rs.getLong("id"), new FeedAuthor(
                    rs.getString("public_id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    rs.getString("profile_image_url"),
                    rs.getString("region")));
        });
        return authors;
    }

    @Override
    public Map<Long, List<String>> findPhotoUrls(Collection<Long> feedIds) {
        if (feedIds.isEmpty()) {
            return Map.of();
        }
        // ORDER BY로 정렬된 순서를 그대로 지키려고 게시물별 목록에 차례로 담음
        Map<Long, List<String>> photos = new LinkedHashMap<>();
        jdbc.query(PHOTOS_SQL, new MapSqlParameterSource("ids", feedIds), rs -> {
            photos.computeIfAbsent(rs.getLong("feed_id"), id -> new ArrayList<>()).add(rs.getString("url"));
        });
        return photos;
    }

    @Override
    public Map<Long, List<FeedViewPlace>> findPlaces(Collection<Long> feedIds, Long viewerId) {
        if (feedIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", feedIds).addValue("viewerId", viewerId);
        Map<Long, List<FeedViewPlace>> places = new LinkedHashMap<>();
        jdbc.query(PLACES_SQL, params, rs -> {
            places.computeIfAbsent(rs.getLong("feed_id"), id -> new ArrayList<>()).add(new FeedViewPlace(
                    rs.getString("public_id"),
                    rs.getString("name"),
                    rs.getString("address"),
                    rs.getString("region"),
                    rs.getDouble("lat"),
                    rs.getDouble("lng"),
                    rs.getString("category"),
                    rs.getString("source_type"),
                    rs.getString("visibility"),
                    rs.getString("description")));
        });
        return places;
    }

    @Override
    public Set<Long> findLikedFeedIds(long viewerId, Collection<Long> feedIds) {
        return findFeedIds(LIKED_SQL, viewerId, feedIds);
    }

    @Override
    public Set<Long> findBookmarkedFeedIds(long viewerId, Collection<Long> feedIds) {
        return findFeedIds(BOOKMARKED_SQL, viewerId, feedIds);
    }

    private Set<Long> findFeedIds(String sql, long viewerId, Collection<Long> feedIds) {
        if (feedIds.isEmpty()) {
            return Set.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", feedIds).addValue("viewerId", viewerId);
        return new HashSet<>(jdbc.queryForList(sql, params, Long.class));
    }
}
