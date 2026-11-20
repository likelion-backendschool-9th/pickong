package com.pickong.savedplace.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 저장 장소의 개인 사진. SavedPlace 애그리거트 안에서만 다룹니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SavedPlacePhoto {
    private Long id;
    private Long mediaFileId;
    private int sortOrder;
}
