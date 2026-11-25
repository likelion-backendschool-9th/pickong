package com.pickong.savedplace.application.dto;

import java.time.OffsetDateTime;

/**
 * 내 저장 장소 한 건.
 *
 * @param visitStatus VisitStatus 이름
 * @param source SavedPlaceSource 이름. 출처를 모르면 null
 */
public record SavedPlaceResult(
        long id,
        SavedPlaceInfo place,
        String visitStatus,
        String memo,
        String source,
        OffsetDateTime savedAt) {
}
