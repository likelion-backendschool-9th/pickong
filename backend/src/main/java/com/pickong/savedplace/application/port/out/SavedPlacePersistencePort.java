package com.pickong.savedplace.application.port.out;

import com.pickong.savedplace.domain.model.SavedPlace;
import java.util.Collection;
import java.util.List;

/** 저장 장소 쓰기 저장소. saved_places만 다룹니다. */
public interface SavedPlacePersistencePort {

    /**
     * 아직 저장하지 않은 장소면 저장하고 true, 이미 저장되어 있으면 아무것도 하지 않고 false를 돌려줍니다.
     * 같은 회원 · 같은 장소를 동시에 저장해도 UNIQUE(user_id, place_id) 위반 없이 한 건만 남습니다.
     */
    boolean saveIfAbsent(SavedPlace savedPlace);

    /** 회원의 저장 장소 중 ids에 해당하는 것. 다른 회원 것은 빠집니다. */
    List<SavedPlace> findMine(long userId, Collection<Long> ids);

    /** 방문 상태 · 메모 등 바뀐 값을 저장합니다. */
    void updateAll(List<SavedPlace> savedPlaces);

    /** 회원의 저장 장소 중 ids를 지웁니다. 다른 회원 것은 지우지 않습니다. */
    void deleteMine(long userId, Collection<Long> ids);
}
