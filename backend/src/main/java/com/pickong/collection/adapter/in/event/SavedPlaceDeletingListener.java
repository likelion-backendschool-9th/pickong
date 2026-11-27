package com.pickong.collection.adapter.in.event;

import com.pickong.collection.application.port.in.CollectionPlaceCleanupUseCase;
import com.pickong.savedplace.domain.event.SavedPlaceDeletingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 저장 장소 삭제 직전(SavedPlaceDeletingEvent)에 그 장소를 담은 컬렉션 구성을 먼저 지웁니다.
 * <p>
 * collection_places.saved_place_id는 ON DELETE RESTRICT라, 이 정리가 없으면 저장 장소 삭제가 FK 위반으로 실패합니다.
 * <b>동기 · 같은 트랜잭션</b>(@EventListener)이라 여기서 실패하면 저장 장소 삭제도 함께 되돌아갑니다 (backend_blueprint.md 6.3).
 */
@Component
public class SavedPlaceDeletingListener {

    private final CollectionPlaceCleanupUseCase cleanup;

    public SavedPlaceDeletingListener(CollectionPlaceCleanupUseCase cleanup) {
        this.cleanup = cleanup;
    }

    @EventListener
    public void on(SavedPlaceDeletingEvent event) {
        cleanup.removeSavedPlaces(event.savedPlaceIds());
    }
}
