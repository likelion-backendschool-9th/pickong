package com.pickong.savedplace.adapter.out.persistence.query;

import com.pickong.savedplace.application.port.out.SavedPlaceTargetLookupPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 저장할 장소와 출처 게시물 · 컬렉션의 공개 id → 내부 id. places(place) · feeds · feed_places(feed) ·
 * collections · collection_places(collection) · follows(social)를 읽기만 합니다 (backend_blueprint.md 6.1 조회 예외).
 * 다른 모듈의 저장소 · 엔티티는 쓰지 않습니다.
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

    /** 숨김 게시물은 작성자도 연결하지 않습니다 (Feed.ensureAcceptsInteraction과 같은 기준). */
    private static final String LINKABLE_FEED = """
            SELECT f.id
              FROM feeds f
             WHERE f.public_id = :publicId
               AND f.deleted_at IS NULL
               AND f.hidden_at IS NULL
               AND %s
            """.formatted(SourceVisibilitySql.readableBy("f", ":userId"));

    private static final String FEED_PLACE_IDS = "SELECT fp.place_id FROM feed_places fp WHERE fp.feed_id = :id";

    /** 유료 컬렉션(price)은 구매 기록 테이블이 생기기 전까지 본인 것만 연결합니다. */
    private static final String LINKABLE_COLLECTION = """
            SELECT c.id
              FROM collections c
             WHERE c.public_id = :publicId
               AND c.deleted_at IS NULL
               AND c.hidden_at IS NULL
               AND (c.price IS NULL OR c.user_id = :userId)
               AND %s
            """.formatted(SourceVisibilitySql.readableBy("c", ":userId"));

    private static final String COLLECTION_PLACE_IDS =
            "SELECT cp.place_id FROM collection_places cp WHERE cp.collection_id = :id";

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
    public Optional<SourceRef> findLinkableFeed(long userId, UUID feedPublicId) {
        return linkable(LINKABLE_FEED, FEED_PLACE_IDS, userId, feedPublicId);
    }

    @Override
    public Optional<SourceRef> findLinkableCollection(long userId, UUID collectionPublicId) {
        return linkable(LINKABLE_COLLECTION, COLLECTION_PLACE_IDS, userId, collectionPublicId);
    }

    private Optional<SourceRef> linkable(String sourceSql, String placeIdsSql, long userId, UUID publicId) {
        MapSqlParameterSource params = new MapSqlParameterSource("publicId", publicId).addValue("userId", userId);
        List<Long> ids = jdbc.queryForList(sourceSql, params, Long.class);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        long id = ids.getFirst();
        List<Long> placeIds = jdbc.queryForList(placeIdsSql, new MapSqlParameterSource("id", id), Long.class);
        return Optional.of(new SourceRef(id, Set.copyOf(placeIds)));
    }
}
