package com.pickong.savedplace.adapter.in.web.response;

import com.pickong.savedplace.adapter.in.web.SourceTypes;
import com.pickong.savedplace.application.dto.SavedPlaceInfo;
import com.pickong.savedplace.application.dto.SavedPlaceResult;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 내 저장 장소. 프론트 SavedPlaceDto와 같은 필드입니다.
 *
 * @param id 저장 장소 id. 본인만 쓰는 값이라 내부 id를 문자열로 보냅니다.
 * @param collectionIds 컬렉션 테이블이 아직 없어 항상 빈 목록입니다.
 */
public record SavedPlaceResponse(
        String id,
        PlaceResponse place,
        List<String> collectionIds,
        String visitStatus,
        String memo,
        OffsetDateTime savedAt,
        SourceResponse source) {

    public static SavedPlaceResponse from(SavedPlaceResult result) {
        return new SavedPlaceResponse(
                String.valueOf(result.id()),
                PlaceResponse.from(result.place()),
                List.of(),
                result.visitStatus(),
                result.memo(),
                result.savedAt(),
                SourceResponse.from(result.source(), result.sourceRefId(), result.sourceLabel()));
    }

    /**
     * 장소. 프론트 PlaceDto와 같은 필드입니다.
     *
     * @param id 장소 공개 id
     * @param businessHours 영업시간을 아직 수집하지 않아 항상 null입니다.
     */
    public record PlaceResponse(
            String id,
            String name,
            String address,
            String region,
            double lat,
            double lng,
            String category,
            boolean isCustom,
            String visibility,
            String description,
            String imageUrl,
            Object businessHours) {

        private static final String CUSTOM = "CUSTOM";

        private static PlaceResponse from(SavedPlaceInfo place) {
            return new PlaceResponse(
                    place.publicId().toString(),
                    place.name(),
                    place.address(),
                    place.region(),
                    place.latitude(),
                    place.longitude(),
                    place.category(),
                    CUSTOM.equals(place.sourceType()),
                    place.visibility(),
                    place.description(),
                    place.imageUrl(),
                    null);
        }
    }

    /**
     * 저장 출처. type은 프론트 SavedPlaceSourceType 값입니다 (SourceTypes).
     *
     * @param refId 게시물 출처면 게시물 공개 id, 링크 가져오기면 원문 URL. 없으면 null
     * @param label 저장할 때 받은 문구. 없으면 종류별 기본 문구
     */
    public record SourceResponse(String type, String refId, String label) {

        private static SourceResponse from(String source, String refId, String label) {
            String type = SourceTypes.toApi(source);
            return new SourceResponse(type, refId, label == null ? SourceTypes.defaultLabel(type) : label);
        }
    }
}
