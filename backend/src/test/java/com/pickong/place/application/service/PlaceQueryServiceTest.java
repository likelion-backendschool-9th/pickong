package com.pickong.place.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.error.ErrorCode;
import com.pickong.place.application.dto.PlaceCluster;
import com.pickong.place.application.dto.PlaceMapGroup;
import com.pickong.place.application.dto.PlaceMapItem;
import com.pickong.place.application.dto.PlaceMapQuery;
import com.pickong.place.application.port.out.PlaceMapQueryPort;
import com.pickong.place.application.port.out.PlacePersistencePort;
import com.pickong.place.domain.PlaceErrorCode;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceQueryServiceTest {

    private static final PlaceMapQuery QUERY = new PlaceMapQuery(127.0, 37.4, 127.1, 37.5, null);

    @Mock
    PlacePersistencePort places;

    @Mock
    PlaceMapQueryPort placeMap;

    PlaceQueryService service;

    @BeforeEach
    void setUp() {
        service = new PlaceQueryService(places, placeMap);
    }

    @Test
    @DisplayName("좌표가 기준 이하면 격자로 묶지 않고 혼자인 장소와 같은 좌표 묶음을 나눈다")
    void fewSpots() {
        given(placeMap.groupBySpot(QUERY, PlaceQueryService.MAX_SPOTS + 1)).willReturn(List.of(single(), cluster()));

        var result = service.findMap(QUERY);

        assertThat(result.places()).hasSize(1);
        assertThat(result.clusters()).extracting(PlaceCluster::count).containsExactly(3L);
        verify(placeMap, never()).groupByGrid(any(), anyDouble());
    }

    @Test
    @DisplayName("좌표가 기준과 같으면 격자로 묶지 않는다")
    void exactlyMaxSpots() {
        given(placeMap.groupBySpot(QUERY, PlaceQueryService.MAX_SPOTS + 1))
                .willReturn(Collections.nCopies(PlaceQueryService.MAX_SPOTS, single()));

        assertThat(service.findMap(QUERY).places()).hasSize(PlaceQueryService.MAX_SPOTS);
        verify(placeMap, never()).groupByGrid(any(), anyDouble());
    }

    @Test
    @DisplayName("좌표가 기준을 넘으면 격자로 묶은 결과를 돌려준다")
    void manySpots() {
        given(placeMap.groupBySpot(QUERY, PlaceQueryService.MAX_SPOTS + 1))
                .willReturn(Collections.nCopies(PlaceQueryService.MAX_SPOTS + 1, single()));
        given(placeMap.groupByGrid(eq(QUERY), anyDouble())).willReturn(List.of(cluster(), single()));

        var result = service.findMap(QUERY);

        assertThat(result.places()).hasSize(1);
        assertThat(result.clusters()).hasSize(1);
    }

    @Test
    @DisplayName("목록은 최대 개수를 전달한다")
    void list() {
        given(placeMap.list(QUERY, PlaceQueryService.MAX_LIST)).willReturn(List.of());

        assertThat(service.findInBounds(QUERY)).isEmpty();
        verify(placeMap).list(QUERY, PlaceQueryService.MAX_LIST);
    }

    @Test
    @DisplayName("잘못된 범위는 조회하지 않고 거절한다")
    void invalidBounds() {
        assertRejected(new PlaceMapQuery(127.0, 37.0, 127.1, 91.0, null), PlaceErrorCode.INVALID_COORDINATES);
        assertRejected(new PlaceMapQuery(-181.0, 37.0, 127.1, 38.0, null), PlaceErrorCode.INVALID_COORDINATES);
        assertRejected(new PlaceMapQuery(127.1, 37.0, 127.0, 38.0, null), CommonErrorCode.INVALID_INPUT);
        assertRejected(new PlaceMapQuery(127.0, 38.0, 127.1, 37.0, null), CommonErrorCode.INVALID_INPUT);
        verifyNoInteractions(placeMap);
    }

    @Test
    @DisplayName("빈 카테고리는 전체로 본다")
    void blankCategory() {
        assertThat(new PlaceMapQuery(127, 37, 128, 38, " ").category()).isNull();
        assertThat(new PlaceMapQuery(127, 37, 128, 38, " CAFE ").category()).isEqualTo("CAFE");
    }

    private void assertRejected(PlaceMapQuery query, ErrorCode code) {
        assertThatThrownBy(() -> service.findMap(query))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
        assertThatThrownBy(() -> service.findInBounds(query))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static PlaceMapGroup single() {
        return new PlaceMapGroup(
                new PlaceCluster(37.45, 127.05, 1, 127.05, 37.45, 127.05, 37.45),
                new PlaceMapItem(UUID.randomUUID(), "책방", "서울", 37.45, 127.05, "SHOP", "SYSTEM"));
    }

    private static PlaceMapGroup cluster() {
        return new PlaceMapGroup(new PlaceCluster(37.45, 127.05, 3, 127.04, 37.44, 127.06, 37.46), null);
    }
}
