package com.pickong.savedplace.domain.event;

import java.util.List;

/**
 * 저장 장소를 지우기 직전에 같은 트랜잭션에서 발행합니다 (backend_blueprint.md 6.3).
 * collection의 SavedPlaceDeletingListener가 collection_places를 먼저 정리합니다(FK RESTRICT).
 *
 * @param userId        저장 장소 주인
 * @param savedPlaceIds 지울 저장 장소 id (본인 것만)
 */
public record SavedPlaceDeletingEvent(long userId, List<Long> savedPlaceIds) {

    public SavedPlaceDeletingEvent {
        savedPlaceIds = List.copyOf(savedPlaceIds);
    }
}
