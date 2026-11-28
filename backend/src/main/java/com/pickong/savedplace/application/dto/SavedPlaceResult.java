package com.pickong.savedplace.application.dto;

import java.time.OffsetDateTime;

/**
 * 내 저장 장소 한 건.
 *
 * @param visitStatus VisitStatus 이름
 * @param source      SavedPlaceSource 이름 (saved_places.source_type)
 * @param sourceRefId FEED면 원본 게시물 공개 id, LINK_IMPORT면 원문 URL. 없으면 null
 * @param sourceLabel 저장할 때 받은 출처 문구. 없으면 null
 */
public record SavedPlaceResult(
        long id,
        SavedPlaceInfo place,
        String visitStatus,
        String memo,
        String source,
        String sourceRefId,
        String sourceLabel,
        OffsetDateTime savedAt) {
}
