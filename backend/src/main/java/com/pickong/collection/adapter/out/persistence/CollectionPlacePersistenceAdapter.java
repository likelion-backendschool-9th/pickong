package com.pickong.collection.adapter.out.persistence;

import com.pickong.collection.application.port.out.CollectionPlaceCommandPort;
import java.util.Collection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * collection_places · collections 쓰기. 지우기와 장소 수 줄이기를 한 문장으로 처리해,
 * 지운 행 수와 place_count가 어긋나지 않게 합니다.
 */
@Component
public class CollectionPlacePersistenceAdapter implements CollectionPlaceCommandPort {

    private static final String DELETE_BY_SAVED_PLACES = """
            WITH removed AS (
                DELETE FROM collection_places
                 WHERE saved_place_id IN (:savedPlaceIds)
             RETURNING collection_id
            )
            UPDATE collections c
               SET place_count = GREATEST(c.place_count - r.removed_count, 0),
                   updated_at = now()
              FROM (SELECT collection_id, count(*) AS removed_count FROM removed GROUP BY collection_id) r
             WHERE c.id = r.collection_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CollectionPlacePersistenceAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void deleteBySavedPlaceIds(Collection<Long> savedPlaceIds) {
        jdbc.update(DELETE_BY_SAVED_PLACES, new MapSqlParameterSource("savedPlaceIds", savedPlaceIds));
    }
}
