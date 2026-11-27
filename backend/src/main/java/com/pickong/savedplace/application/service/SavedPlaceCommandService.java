package com.pickong.savedplace.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.savedplace.application.dto.SaveSavedPlacesCommand;
import com.pickong.savedplace.application.dto.SaveSavedPlacesResult;
import com.pickong.savedplace.application.dto.SavedPlaceResult;
import com.pickong.savedplace.application.dto.UpdateSavedPlacesCommand;
import com.pickong.savedplace.application.port.in.SavedPlaceCommandUseCase;
import com.pickong.savedplace.application.port.out.SavedPlacePersistencePort;
import com.pickong.savedplace.application.port.out.SavedPlaceQueryPort;
import com.pickong.savedplace.application.port.out.SavedPlaceTargetLookupPort;
import com.pickong.savedplace.application.port.out.SavedPlaceTargetLookupPort.SourceRef;
import com.pickong.savedplace.domain.SavedPlaceErrorCode;
import com.pickong.savedplace.domain.event.SavedPlaceDeletingEvent;
import com.pickong.savedplace.domain.model.SavedPlace;
import com.pickong.savedplace.domain.vo.SavedPlaceOrigin;
import com.pickong.savedplace.domain.vo.SavedPlaceSource;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장 장소 저장 · 수정 · 삭제 (backend_blueprint.md 4장 savedplace).
 * <p>컬렉션 필드(collectionIds 등)는 collections 마이그레이션 전이라 다루지 않습니다 (8.1 #13).
 */
@Service
@Transactional
public class SavedPlaceCommandService implements SavedPlaceCommandUseCase {

    /** 한 요청에서 다루는 최대 개수. 게시물(장소 10곳) · 링크 가져오기 · 목록 여러 개 선택을 넉넉히 덮습니다. */
    public static final int MAX_IDS_PER_REQUEST = 100;

    private final SavedPlacePersistencePort savedPlaces;
    private final SavedPlaceQueryPort savedPlaceQuery;
    private final SavedPlaceTargetLookupPort targets;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SavedPlaceCommandService(
            SavedPlacePersistencePort savedPlaces,
            SavedPlaceQueryPort savedPlaceQuery,
            SavedPlaceTargetLookupPort targets,
            ApplicationEventPublisher events,
            Clock clock) {
        this.savedPlaces = savedPlaces;
        this.savedPlaceQuery = savedPlaceQuery;
        this.targets = targets;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public SaveSavedPlacesResult save(SaveSavedPlacesCommand command) {
        List<UUID> placeIds = List.copyOf(new LinkedHashSet<>(command.placeIds()));
        if (placeIds.isEmpty()) {
            throw new BusinessException(SavedPlaceErrorCode.NO_PLACE_SELECTED);
        }
        checkSize(placeIds.size());
        // 하나라도 없거나 볼 수 없으면 아무것도 저장하지 않습니다.
        Map<UUID, Long> visible = targets.findVisiblePlaceIds(command.userId(), placeIds);
        if (!visible.keySet().containsAll(placeIds)) {
            throw new BusinessException(SavedPlaceErrorCode.PLACE_NOT_FOUND);
        }

        Optional<SourceRef> source = linkableSource(command);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<UUID> created = new ArrayList<>();
        List<UUID> linked = new ArrayList<>();
        for (UUID placeId : placeIds) {
            long internalPlaceId = visible.get(placeId);
            SavedPlace savedPlace = SavedPlace.create(command.userId(), internalPlaceId,
                    origin(command, source, internalPlaceId), now);
            (savedPlaces.saveIfAbsent(savedPlace) ? created : linked).add(placeId);
        }
        return new SaveSavedPlacesResult(created, linked);
    }

    @Override
    public List<SavedPlaceResult> update(UpdateSavedPlacesCommand command) {
        List<Long> ids = distinct(command.ids());
        if (ids.isEmpty()) {
            return List.of();
        }
        checkSize(ids.size());
        List<SavedPlace> mine = savedPlaces.findMine(command.userId(), ids);
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (SavedPlace savedPlace : mine) {
            if (command.visitStatus() != null) {
                savedPlace.changeVisitStatus(command.visitStatus(), now);
            }
            if (command.memo() != null) {
                savedPlace.changeMemo(command.memo(), now);
            }
        }
        savedPlaces.updateAll(mine);
        return savedPlaceQuery.findByUserIdAndIds(command.userId(), mine.stream().map(SavedPlace::getId).toList());
    }

    @Override
    public void delete(long userId, List<Long> ids) {
        List<Long> distinct = distinct(ids);
        if (distinct.isEmpty()) {
            return;
        }
        checkSize(distinct.size());
        List<Long> mine = savedPlaces.findMine(userId, distinct).stream().map(SavedPlace::getId).toList();
        if (mine.isEmpty()) {
            return;
        }
        // 컬렉션 구성 장소(collection_places)를 먼저 정리할 수 있도록 지우기 직전에 같은 트랜잭션에서 알립니다 (6.3).
        events.publishEvent(new SavedPlaceDeletingEvent(userId, mine));
        savedPlaces.deleteMine(userId, mine);
    }

    /**
     * 출처 게시물 · 컬렉션을 찾습니다. 없거나, 회원이 볼 수 없거나(공개 범위 · 숨김), 유료 컬렉션이면 비어 있습니다.
     * 이때는 저장을 막지 않고 출처 id 없이 종류 · 문구만 남깁니다. 볼 수 없는 출처가 있다는 사실도 드러내지 않습니다.
     */
    private Optional<SourceRef> linkableSource(SaveSavedPlacesCommand command) {
        long userId = command.userId();
        return switch (sourceType(command)) {
            case FEED -> publicId(command.sourceRefId()).flatMap(id -> targets.findLinkableFeed(userId, id));
            case COLLECTION -> publicId(command.sourceRefId()).flatMap(id -> targets.findLinkableCollection(userId, id));
            default -> Optional.empty();
        };
    }

    /** 출처 id는 그 출처에 실제로 들어 있는 장소에만 연결합니다. */
    private static SavedPlaceOrigin origin(SaveSavedPlacesCommand command, Optional<SourceRef> source, long placeId) {
        SavedPlaceSource type = sourceType(command);
        Long refId = source.filter(ref -> ref.contains(placeId)).map(SourceRef::id).orElse(null);
        String url = type == SavedPlaceSource.LINK_IMPORT ? command.sourceRefId() : null;
        return new SavedPlaceOrigin(type, refId, command.sourceLabel(), url);
    }

    private static SavedPlaceSource sourceType(SaveSavedPlacesCommand command) {
        return command.sourceType() == null ? SavedPlaceSource.SEARCH : command.sourceType();
    }

    /** 공개 id가 UUID가 아니면 그런 게시물 · 컬렉션은 없습니다. */
    private static Optional<UUID> publicId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException notUuid) {
            return Optional.empty();
        }
    }

    private static List<Long> distinct(List<Long> ids) {
        return ids == null ? List.of() : List.copyOf(new LinkedHashSet<>(ids));
    }

    private static void checkSize(int size) {
        if (size > MAX_IDS_PER_REQUEST) {
            throw new BusinessException(SavedPlaceErrorCode.TOO_MANY_PLACES);
        }
    }
}
