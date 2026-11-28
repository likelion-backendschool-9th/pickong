package com.pickong.savedplace.adapter.in.web.response;

import com.pickong.savedplace.application.dto.SaveSavedPlacesResult;
import java.util.List;
import java.util.UUID;

/** 프론트 SaveResult와 같은 형식입니다. 장소 공개 id를 문자열로 보냅니다. */
public record SaveSavedPlacesResponse(List<String> createdPlaceIds, List<String> linkedPlaceIds) {

    public static SaveSavedPlacesResponse from(SaveSavedPlacesResult result) {
        return new SaveSavedPlacesResponse(
                result.createdPlaceIds().stream().map(UUID::toString).toList(),
                result.linkedPlaceIds().stream().map(UUID::toString).toList());
    }
}
