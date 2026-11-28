package com.pickong.savedplace.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * 저장 결과.
 *
 * @param createdPlaceIds 새로 저장한 장소 공개 id
 * @param linkedPlaceIds  이미 저장되어 있던 장소 공개 id (새로 만들지 않음)
 */
public record SaveSavedPlacesResult(List<UUID> createdPlaceIds, List<UUID> linkedPlaceIds) {

    public SaveSavedPlacesResult {
        createdPlaceIds = List.copyOf(createdPlaceIds);
        linkedPlaceIds = List.copyOf(linkedPlaceIds);
    }
}
