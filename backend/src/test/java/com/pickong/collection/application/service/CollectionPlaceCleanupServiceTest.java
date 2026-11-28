package com.pickong.collection.application.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pickong.collection.application.port.out.CollectionPlaceCommandPort;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionPlaceCleanupServiceTest {

    @Mock
    CollectionPlaceCommandPort collectionPlaces;

    @InjectMocks
    CollectionPlaceCleanupService service;

    @Test
    @DisplayName("지울 저장 장소를 담은 컬렉션 구성을 정리한다")
    void removes() {
        service.removeSavedPlaces(List.of(1L, 2L));

        verify(collectionPlaces).deleteBySavedPlaceIds(List.of(1L, 2L));
    }

    @Test
    @DisplayName("지울 것이 없으면 아무것도 하지 않는다")
    void nothing() {
        service.removeSavedPlaces(List.of());

        verifyNoInteractions(collectionPlaces);
    }
}
