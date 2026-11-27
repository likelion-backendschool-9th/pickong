package com.pickong.savedplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.ErrorCode;
import com.pickong.savedplace.application.dto.SaveSavedPlacesCommand;
import com.pickong.savedplace.application.dto.SaveSavedPlacesResult;
import com.pickong.savedplace.application.dto.UpdateSavedPlacesCommand;
import com.pickong.savedplace.application.port.out.SavedPlacePersistencePort;
import com.pickong.savedplace.application.port.out.SavedPlaceQueryPort;
import com.pickong.savedplace.application.port.out.SavedPlaceTargetLookupPort;
import com.pickong.savedplace.domain.SavedPlaceErrorCode;
import com.pickong.savedplace.domain.event.SavedPlaceDeletingEvent;
import com.pickong.savedplace.domain.model.SavedPlace;
import com.pickong.savedplace.domain.vo.SavedPlaceSource;
import com.pickong.savedplace.domain.vo.VisitStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SavedPlaceCommandServiceTest {

    private static final long ME = 1L;
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    SavedPlacePersistencePort savedPlaces;

    @Mock
    SavedPlaceQueryPort query;

    @Mock
    SavedPlaceTargetLookupPort targets;

    @Mock
    ApplicationEventPublisher events;

    SavedPlaceCommandService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC);
        service = new SavedPlaceCommandService(savedPlaces, query, targets, events, clock);
    }

    @Test
    @DisplayName("중복 id는 하나로 합치고, 새로 넣었는지에 따라 created · linked로 나눈다")
    void saveSplitsCreatedAndLinked() {
        given(targets.findVisiblePlaceIds(eq(ME), any())).willReturn(Map.of(A, 10L, B, 20L));
        given(savedPlaces.saveIfAbsent(any())).willReturn(true, false);

        SaveSavedPlacesResult result = service.save(new SaveSavedPlacesCommand(ME, List.of(A, B, A), null, null, null));

        assertThat(result.createdPlaceIds()).containsExactly(A);
        assertThat(result.linkedPlaceIds()).containsExactly(B);
        ArgumentCaptor<SavedPlace> saved = ArgumentCaptor.forClass(SavedPlace.class);
        verify(savedPlaces, org.mockito.Mockito.times(2)).saveIfAbsent(saved.capture());
        assertThat(saved.getAllValues()).extracting(SavedPlace::getPlaceId).containsExactly(10L, 20L);
        assertThat(saved.getAllValues()).allSatisfy(s -> assertThat(s.getSourceType()).isEqualTo(SavedPlaceSource.SEARCH));
    }

    @Test
    @DisplayName("볼 수 없는 장소가 하나라도 있으면 404이고 아무것도 저장하지 않는다")
    void saveRejectsInvisiblePlace() {
        given(targets.findVisiblePlaceIds(eq(ME), any())).willReturn(Map.of(A, 10L));

        assertRejected(() -> service.save(new SaveSavedPlacesCommand(ME, List.of(A, B), null, null, null)),
                SavedPlaceErrorCode.PLACE_NOT_FOUND);
        verify(savedPlaces, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("빈 목록과 한도를 넘는 목록은 조회 없이 거절한다")
    void saveRejectsEmptyOrTooMany() {
        assertRejected(() -> service.save(new SaveSavedPlacesCommand(ME, List.of(), null, null, null)),
                SavedPlaceErrorCode.NO_PLACE_SELECTED);
        List<UUID> many = IntStream.rangeClosed(0, SavedPlaceCommandService.MAX_IDS_PER_REQUEST)
                .mapToObj(i -> new UUID(0, i)).toList();
        assertRejected(() -> service.save(new SaveSavedPlacesCommand(ME, many, null, null, null)),
                SavedPlaceErrorCode.TOO_MANY_PLACES);
        verifyNoInteractions(targets, savedPlaces);
    }

    @Test
    @DisplayName("게시물 출처는 공개 id로 게시물 내부 id를 찾아 연결하고, 공개 id가 UUID가 아니면 연결하지 않는다")
    void feedOrigin() {
        UUID feed = UUID.randomUUID();
        given(targets.findVisiblePlaceIds(eq(ME), any())).willReturn(Map.of(A, 10L));
        given(targets.findFeedId(feed)).willReturn(Optional.of(77L));
        given(savedPlaces.saveIfAbsent(any())).willReturn(true);
        ArgumentCaptor<SavedPlace> saved = ArgumentCaptor.forClass(SavedPlace.class);

        service.save(new SaveSavedPlacesCommand(ME, List.of(A), SavedPlaceSource.FEED, feed.toString(), "게시물"));
        service.save(new SaveSavedPlacesCommand(ME, List.of(A), SavedPlaceSource.FEED, "not-uuid", null));

        verify(savedPlaces, org.mockito.Mockito.times(2)).saveIfAbsent(saved.capture());
        assertThat(saved.getAllValues()).extracting(SavedPlace::getSourceFeedId).containsExactly(77L, null);
        assertThat(saved.getAllValues().getFirst().getSourceLabel()).isEqualTo("게시물");
    }

    @Test
    @DisplayName("컬렉션 출처는 공개 id로 컬렉션 내부 id를 찾아 연결한다")
    void collectionOrigin() {
        UUID collection = UUID.randomUUID();
        given(targets.findVisiblePlaceIds(eq(ME), any())).willReturn(Map.of(A, 10L));
        given(targets.findCollectionId(collection)).willReturn(Optional.of(55L));
        given(savedPlaces.saveIfAbsent(any())).willReturn(true);
        ArgumentCaptor<SavedPlace> saved = ArgumentCaptor.forClass(SavedPlace.class);

        service.save(new SaveSavedPlacesCommand(ME, List.of(A), SavedPlaceSource.COLLECTION, collection.toString(), null));

        verify(savedPlaces).saveIfAbsent(saved.capture());
        assertThat(saved.getValue().getSourceCollectionId()).isEqualTo(55L);
        assertThat(saved.getValue().getSourceFeedId()).isNull();
    }

    @Test
    @DisplayName("수정은 본인 것만 바꾸고 바뀐 행만 다시 읽는다. 보내지 않은 값은 그대로")
    void updateOnlyMine() {
        SavedPlace mine = savedPlace(5L);
        given(savedPlaces.findMine(ME, List.of(5L, 6L))).willReturn(List.of(mine));

        service.update(new UpdateSavedPlacesCommand(ME, List.of(5L, 6L, 5L), VisitStatus.VISITED, null));

        assertThat(mine.getVisitStatus()).isEqualTo(VisitStatus.VISITED);
        assertThat(mine.getMemo()).isEqualTo("원래 메모");
        verify(savedPlaces).updateAll(List.of(mine));
        verify(query).findByUserIdAndIds(ME, List.of(5L));
    }

    @Test
    @DisplayName("수정할 id가 없으면 아무것도 하지 않는다")
    void updateNothing() {
        assertThat(service.update(new UpdateSavedPlacesCommand(ME, List.of(), VisitStatus.VISITED, null))).isEmpty();
        verifyNoInteractions(savedPlaces, query);
    }

    @Test
    @DisplayName("삭제는 본인 것만, 지우기 전에 이벤트를 발행한다")
    void deleteMineWithEvent() {
        given(savedPlaces.findMine(ME, List.of(5L, 6L))).willReturn(List.of(savedPlace(5L)));

        service.delete(ME, List.of(5L, 6L));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(events, savedPlaces);
        order.verify(events).publishEvent(new SavedPlaceDeletingEvent(ME, List.of(5L)));
        order.verify(savedPlaces).deleteMine(ME, List.of(5L));
    }

    @Test
    @DisplayName("지울 본인 것이 없으면 이벤트도 삭제도 없다")
    void deleteNothingMine() {
        given(savedPlaces.findMine(anyLong(), any())).willReturn(Collections.emptyList());

        service.delete(ME, new ArrayList<>(List.of(9L)));

        verifyNoInteractions(events);
        verify(savedPlaces, never()).deleteMine(anyLong(), any());
    }

    private static SavedPlace savedPlace(long id) {
        return SavedPlace.builder()
                .id(id)
                .userId(ME)
                .placeId(10L)
                .memo("원래 메모")
                .visitStatus(VisitStatus.WANT_TO_GO)
                .sourceType(SavedPlaceSource.SEARCH)
                .build();
    }

    private static void assertRejected(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
