package com.pickong.savedplace.application.port.out;

import com.pickong.savedplace.application.dto.SavedPlaceResult;
import java.util.Collection;
import java.util.List;

/** 저장 장소 화면 조회. */
public interface SavedPlaceQueryPort {

    /** 회원이 저장했고 지금 볼 수 있는 장소. 최근에 저장한 순서입니다. */
    List<SavedPlaceResult> findByUserId(long userId);

    /** findByUserId 중 ids에 해당하는 것만. 같은 순서입니다. */
    List<SavedPlaceResult> findByUserIdAndIds(long userId, Collection<Long> ids);
}
