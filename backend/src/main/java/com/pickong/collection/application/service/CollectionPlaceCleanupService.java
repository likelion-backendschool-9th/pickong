package com.pickong.collection.application.service;

import com.pickong.collection.application.port.in.CollectionPlaceCleanupUseCase;
import com.pickong.collection.application.port.out.CollectionPlaceCommandPort;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장 장소 삭제에 맞춰 컬렉션 구성을 정리합니다. 저장 장소 삭제와 같은 트랜잭션에서 실행됩니다. */
@Service
@Transactional
public class CollectionPlaceCleanupService implements CollectionPlaceCleanupUseCase {

    private final CollectionPlaceCommandPort collectionPlaces;

    public CollectionPlaceCleanupService(CollectionPlaceCommandPort collectionPlaces) {
        this.collectionPlaces = collectionPlaces;
    }

    @Override
    public void removeSavedPlaces(Collection<Long> savedPlaceIds) {
        if (savedPlaceIds == null || savedPlaceIds.isEmpty()) {
            return;
        }
        collectionPlaces.deleteBySavedPlaceIds(savedPlaceIds);
    }
}
