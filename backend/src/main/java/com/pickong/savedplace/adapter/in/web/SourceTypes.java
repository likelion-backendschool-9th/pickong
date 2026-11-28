package com.pickong.savedplace.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.savedplace.domain.SavedPlaceErrorCode;
import com.pickong.savedplace.domain.vo.SavedPlaceSource;
import java.util.Map;

/**
 * 프론트 저장 출처 값(SavedPlaceSourceType) ↔ DB 값(SavedPlaceSource).
 * 프론트는 게시물을 POST라고 부르고, 나머지 이름은 ERD와 같습니다 (backend_blueprint.md 4장 savedplace).
 */
public final class SourceTypes {

    private static final Map<String, SavedPlaceSource> FROM_API = Map.of(
            "POST", SavedPlaceSource.FEED,
            "COLLECTION", SavedPlaceSource.COLLECTION,
            "LINK_IMPORT", SavedPlaceSource.LINK_IMPORT,
            "SEARCH", SavedPlaceSource.SEARCH,
            "CUSTOM", SavedPlaceSource.CUSTOM);

    private static final Map<String, String> DEFAULT_LABEL = Map.of(
            "POST", "게시물에서 저장",
            "COLLECTION", "컬렉션에서 저장",
            "LINK_IMPORT", "링크에서 가져옴",
            "SEARCH", "직접 저장",
            "CUSTOM", "직접 등록한 장소");

    private SourceTypes() {
    }

    /** 요청 값 → DB 값. 없으면 직접 저장(SEARCH). PLAN_VISIT처럼 API로 받지 않는 값은 400입니다. */
    public static SavedPlaceSource fromApi(String type) {
        if (type == null || type.isBlank()) {
            return SavedPlaceSource.SEARCH;
        }
        SavedPlaceSource source = FROM_API.get(type.strip());
        if (source == null) {
            throw new BusinessException(SavedPlaceErrorCode.INVALID_SOURCE);
        }
        return source;
    }

    /** DB 값 → 응답 값. 프론트에 없는 PLAN_VISIT은 SEARCH로 보냅니다. */
    public static String toApi(String source) {
        if (source == null) {
            return "SEARCH";
        }
        return switch (source) {
            case "FEED" -> "POST";
            case "COLLECTION", "LINK_IMPORT", "SEARCH", "CUSTOM" -> source;
            default -> "SEARCH";
        };
    }

    public static String defaultLabel(String apiType) {
        return DEFAULT_LABEL.getOrDefault(apiType, "직접 저장");
    }
}
