package com.pickong.savedplace.application.port.in;

import com.pickong.savedplace.application.dto.SaveSavedPlacesCommand;
import com.pickong.savedplace.application.dto.SaveSavedPlacesResult;
import com.pickong.savedplace.application.dto.SavedPlaceResult;
import com.pickong.savedplace.application.dto.UpdateSavedPlacesCommand;
import java.util.List;

/** 저장 장소 저장 · 수정 · 삭제. 본인이 저장한 장소만 다룹니다 (backend_blueprint.md 4장 savedplace). */
public interface SavedPlaceCommandUseCase {

    /**
     * 장소를 저장합니다. 이미 저장한 장소는 새로 만들지 않고 linkedPlaceIds로 돌려줍니다.
     *
     * @throws com.pickong.common.error.BusinessException 400 NO_PLACE_SELECTED · TOO_MANY_PLACES,
     *         404 PLACE_NOT_FOUND(하나라도 없거나 볼 수 없으면 아무것도 저장하지 않음)
     */
    SaveSavedPlacesResult save(SaveSavedPlacesCommand command);

    /**
     * 방문 상태 · 메모를 바꾸고, 바뀐 뒤의 저장 장소(본인 것, 지금 볼 수 있는 장소)를 돌려줍니다.
     *
     * @throws com.pickong.common.error.BusinessException 400 TOO_MANY_PLACES · MEMO_TOO_LONG
     */
    List<SavedPlaceResult> update(UpdateSavedPlacesCommand command);

    /**
     * 저장 기록만 지웁니다. 공용 장소와 일정의 장소는 지우지 않습니다.
     *
     * @throws com.pickong.common.error.BusinessException 400 TOO_MANY_PLACES
     */
    void delete(long userId, List<Long> ids);
}
