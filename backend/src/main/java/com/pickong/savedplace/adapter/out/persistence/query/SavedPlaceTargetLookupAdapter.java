package com.pickong.savedplace.adapter.out.persistence.query;

import com.pickong.savedplace.application.port.out.SavedPlaceTargetLookupPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 저장할 장소와 출처 게시물 · 컬렉션의 공개 id → 내부 id. places(place) · feeds(feed) · collections(collection)를 읽기만 합니다
 * (backend_blueprint.md 6.1 조회 예외). 다른 모듈의 저장소 · 엔티티는 쓰지 않습니다.
 */
@Component
public class SavedPlaceTargetLookupAdapter implements SavedPlaceTargetLookupPort {

    /** GET /saved-places와 같은 기준: 삭제되지 않았고, 공개이거나 본인이 만든 장소. */
    private static final String VISIBLE_PLACES = """
            SELECT p.public_id, p.id
              FROM places p
             WHERE p.public_id IN (:publicIds)
               AND p.deleted_at IS NULL
               AND (p.visibility = 'PUBLIC' OR p.owner_id = :userId)
            """;

    private static final String FEED_ID = "SELECT f.id FROM feeds f WHERE f.public_id = :publicId AND f.deleted_at IS NULL";

    private static final String COLLECTION_ID =
            "SELECT c.id FROM collections c WHERE c.public_id = :publicId AND c.deleted_at IS NULL";

    private final NamedParameterJdbcTemplate jdbc;

    public SavedPlaceTargetLookupAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Long> findVisiblePlaceIds(long userId, Collection<UUID> placePublicIds) {
        if (placePublicIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("publicIds", placePublicIds).addValue("userId", userId);
        Map<UUID, Long> ids = new HashMap<>();
        jdbc.query(VISIBLE_PLACES, params, rs -> {
            ids.put(rs.getObject("public_id", UUID.class), rs.getLong("id"));
        });
        return ids;
    }

    @Override
    public Optional<Long> findFeedId(UUID feedPublicId) {
        return first(FEED_ID, feedPublicId);
    }

    @Override
    public Optional<Long> findCollectionId(UUID collectionPublicId) {
        return first(COLLECTION_ID, collectionPublicId);
    }

    private Optional<Long> first(String sql, UUID publicId) {
        List<Long> ids = jdbc.queryForList(sql, new MapSqlParameterSource("publicId", publicId), Long.class);
        return ids.stream().findFirst();
    }
}
