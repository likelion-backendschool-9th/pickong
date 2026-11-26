package com.pickong.savedplace.adapter.in.web.response;

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
                SourceResponse.from(result.source()));
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
     * 저장 출처. type은 프론트 SavedPlaceSourceType 값입니다.
     *
     * @param refId 원본의 공개 id를 아직 조회하지 않아 항상 null입니다.
     */
    public record SourceResponse(String type, String refId, String label) {

        private static SourceResponse from(String source) {
            return switch (source) {
                case "FEED" -> new SourceResponse("POST", null, "게시물에서 저장");
                case "COLLECTION_SNAPSHOT" -> new SourceResponse("COLLECTION", null, "컬렉션에서 저장");
                case "AI_IMPORT" -> new SourceResponse("LINK_IMPORT", null, "링크에서 가져옴");
                case null, default -> new SourceResponse("SEARCH", null, "직접 저장");
            };
        }
    }
}
