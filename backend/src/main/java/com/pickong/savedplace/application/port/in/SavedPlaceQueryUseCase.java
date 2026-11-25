package com.pickong.savedplace.application.port.in;

import com.pickong.savedplace.application.dto.SavedPlaceResult;
import java.util.List;

/** 저장 장소 조회. 본인이 저장한 장소만 다룹니다. */
public interface SavedPlaceQueryUseCase {

    /** 내 저장 장소 전체. 최근에 저장한 순서입니다. */
    List<SavedPlaceResult> myList(long userId);
}
