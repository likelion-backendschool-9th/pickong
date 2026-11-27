package com.pickong.savedplace.adapter.out.persistence;

import com.pickong.savedplace.adapter.out.persistence.entity.SavedPlaceJpaEntity;
import com.pickong.savedplace.adapter.out.persistence.repository.SavedPlaceJpaRepository;
import com.pickong.savedplace.application.port.out.SavedPlacePersistencePort;
import com.pickong.savedplace.domain.model.SavedPlace;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 저장 장소 쓰기 저장소.
 * <p>새로 저장할 때는 {@code ON CONFLICT DO NOTHING}으로 넣어, 같은 장소를 동시에 저장해도
 * UNIQUE 위반으로 트랜잭션이 깨지지 않고 한 건만 남습니다. 수정 · 삭제는 JPA로 본인 행만 다룹니다.
 */
@Component
public class SavedPlacePersistenceAdapter implements SavedPlacePersistencePort {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO saved_places (user_id, place_id, memo, visit_status, visited_at,
                                      source_type, source_feed_id, source_collection_id, source_label, source_url,
                                      created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT uk_saved_places_user_place DO NOTHING
            """;

    private final SavedPlaceJpaRepository repository;
    private final JdbcTemplate jdbc;

    public SavedPlacePersistenceAdapter(SavedPlaceJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public boolean saveIfAbsent(SavedPlace s) {
        int inserted = jdbc.update(INSERT_IF_ABSENT,
                s.getUserId(), s.getPlaceId(), s.getMemo(), s.getVisitStatus().name(), s.getVisitedAt(),
                s.getSourceType().name(), s.getSourceFeedId(), s.getSourceCollectionId(), s.getSourceLabel(), s.getSourceUrl(),
                s.getCreatedAt(), s.getUpdatedAt());
        return inserted == 1;
    }

    @Override
    public List<SavedPlace> findMine(long userId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findByUserIdAndIdIn(userId, ids).stream().map(SavedPlaceJpaEntity::toDomain).toList();
    }

    @Override
    public void updateAll(List<SavedPlace> savedPlaces) {
        if (savedPlaces.isEmpty()) {
            return;
        }
        Map<Long, SavedPlace> byId = savedPlaces.stream().collect(Collectors.toMap(SavedPlace::getId, Function.identity()));
        List<SavedPlaceJpaEntity> entities = repository.findAllById(byId.keySet());
        entities.forEach(entity -> entity.apply(byId.get(entity.getId())));
        repository.saveAllAndFlush(entities);
    }

    @Override
    public void deleteMine(long userId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        repository.deleteMine(userId, ids);
    }
}
