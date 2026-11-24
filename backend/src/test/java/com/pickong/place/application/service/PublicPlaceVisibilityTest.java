package com.pickong.place.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.common.domain.GeoPoint;
import com.pickong.place.application.dto.SubmitPlaceCommand;
import com.pickong.place.application.port.out.PlacePersistencePort;
import com.pickong.place.application.port.out.PlaceSubmissionPersistencePort;
import com.pickong.place.application.port.out.SubmitterLookupPort;
import com.pickong.place.domain.model.Place;
import com.pickong.place.domain.model.PlaceSubmission;
import com.pickong.place.domain.vo.PlaceSubmissionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 접수 직후에는 공개 장소가 없고, 승인 후에만 places에 생깁니다. DB는 쓰지 않습니다. */
class PublicPlaceVisibilityTest {

    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");

    private final MemoryPlaces places = new MemoryPlaces();
    private final MemorySubmissions submissions = new MemorySubmissions();
    private PlaceSubmissionService reviews;
    private PlaceQueryService publicPlaces;

    @BeforeEach
    void setUp() {
        SubmitterLookupPort submitters = ids -> Map.of();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        reviews = new PlaceSubmissionService(submissions, places, submitters, clock);
        publicPlaces = new PlaceQueryService(places);
    }

    @Test
    @DisplayName("제보 직후 공개 목록에 없고 승인 후에만 있다")
    void pendingIsHiddenUntilApproved() {
        var submitted = reviews.submit(command("책방"));

        assertThat(submitted.submission().getStatus()).isEqualTo(PlaceSubmissionStatus.PENDING);
        assertThat(submitted.placePublicId()).isNull();
        assertThat(publicPlaces.findPublic()).isEmpty();

        var approved = reviews.approve(submitted.submission().getId(), 7L);

        assertThat(publicPlaces.findPublic()).singleElement().satisfies(place -> {
            assertThat(place.getPublicId()).isEqualTo(approved.placePublicId());
            assertThat(place.getName()).isEqualTo("책방");
        });
    }

    @Test
    @DisplayName("반려한 제보는 공개 목록에 남지 않는다")
    void rejectedIsHidden() {
        var submitted = reviews.submit(command("빈 가게"));

        reviews.reject(submitted.submission().getId(), 7L, "위치가 불명확합니다");

        assertThat(publicPlaces.findPublic()).isEmpty();
    }

    private static SubmitPlaceCommand command(String name) {
        return new SubmitPlaceCommand(3L, name, "서울", "SHOP", "설명", null, 37.5628, 126.9254);
    }

    private static final class MemoryPlaces implements PlacePersistencePort {
        private final List<Place> rows = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Place save(Place place) {
            Place saved = Place.builder()
                    .id(nextId++)
                    .publicId(place.getPublicId())
                    .name(place.getName())
                    .address(place.getAddress())
                    .location(place.getLocation())
                    .category(place.getCategory())
                    .sourceType(place.getSourceType())
                    .createdAt(place.getCreatedAt())
                    .updatedAt(place.getUpdatedAt())
                    .build();
            rows.add(saved);
            return saved;
        }

        @Override
        public Map<Long, UUID> findPublicIdsByIds(Collection<Long> placeIds) {
            Map<Long, UUID> found = new LinkedHashMap<>();
            for (Place place : rows) {
                if (placeIds.contains(place.getId())) {
                    found.put(place.getId(), place.getPublicId());
                }
            }
            return found;
        }

        @Override
        public List<Place> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public List<Place> findInBounds(double west, double south, double east, double north) {
            return rows.stream()
                    .filter(place -> {
                        GeoPoint location = place.getLocation();
                        return location.longitude() >= west && location.longitude() <= east
                                && location.latitude() >= south && location.latitude() <= north;
                    })
                    .toList();
        }
    }

    private static final class MemorySubmissions implements PlaceSubmissionPersistencePort {
        private final Map<Long, PlaceSubmission> rows = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public PlaceSubmission save(PlaceSubmission submission) {
            PlaceSubmission saved = submission.toBuilder().id(nextId++).build();
            rows.put(saved.getId(), saved);
            return saved;
        }

        @Override
        public Optional<PlaceSubmission> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<PlaceSubmission> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public boolean updateIfPending(PlaceSubmission submission) {
            PlaceSubmission current = rows.get(submission.getId());
            if (current == null || current.getStatus() != PlaceSubmissionStatus.PENDING) {
                return false;
            }
            rows.put(submission.getId(), submission);
            return true;
        }
    }
}
