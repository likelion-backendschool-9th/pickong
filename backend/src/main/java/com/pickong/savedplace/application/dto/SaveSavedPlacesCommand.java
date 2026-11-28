package com.pickong.savedplace.application.dto;

import com.pickong.savedplace.domain.vo.SavedPlaceSource;
import java.util.List;
import java.util.UUID;

/**
 * 장소 여러 곳을 한 번에 저장(찜)합니다. POST /saved-places.
 *
 * @param placeIds    장소 공개 id. 중복은 서비스가 하나로 합칩니다
 * @param sourceType  출처 종류. null이면 SEARCH
 * @param sourceRefId FEED면 게시물 공개 id, LINK_IMPORT면 원문 URL. 그 밖에는 쓰지 않습니다
 * @param sourceLabel 화면에 보여 줄 출처 문구
 */
public record SaveSavedPlacesCommand(
        long userId,
        List<UUID> placeIds,
        SavedPlaceSource sourceType,
        String sourceRefId,
        String sourceLabel) {

    public SaveSavedPlacesCommand {
        placeIds = placeIds == null ? List.of() : List.copyOf(placeIds);
    }
}
