package com.pickong.feed.adapter.in.web.response;

import com.pickong.feed.application.dto.FeedPlaceGroup;
import java.util.List;

/**
 * 피드 지도 장소 응답. 프론트 {@code entities/post/api/dto.ts}의 FeedPlaceDto 모양
 * 장소 정보 + 그 장소에 연결된 게시물 id 목록
 *
 * @param postIds   게시물 id. 게시물 응답의 id와 같이 숫자 id의 문자열 (public_id 전환은 #200)
 * @param postCount postIds 개수
 */
public record FeedPlaceResponse(
        PlaceInfo place,
        List<String> postIds,
        int postCount
) {
    /**
     * 프론트 PlaceDto 모양. 피드 지도는 공개 장소만 보여 주므로 visibility는 항상 PUBLIC
     * 장소 상세에만 있는 값(설명, 대표 사진, 영업시간)은 null
     */
    public record PlaceInfo(
            String id,
            String name,
            String address,
            String region,
            double lat,
            double lng,
            String category,
            String sourceType,
            boolean isCustom,
            String visibility,
            String description,
            String imageUrl,
            Object businessHours
    ) {}

    public static FeedPlaceResponse from(FeedPlaceGroup group) {
        PlaceInfo place = new PlaceInfo(
                group.getPlacePublicId(),
                group.getPlaceName(),
                group.getPlaceAddress(),
                group.getPlaceRegion(),
                group.getLat(),
                group.getLng(),
                group.getCategory(),
                group.getSourceType(),
                // 사용자가 지도에서 직접 등록한 장소(CUSTOM)만 isCustom
                "CUSTOM".equals(group.getSourceType()),
                "PUBLIC",
                null,
                null,
                null
        );
        List<String> postIds = group.getFeedIds().stream().map(String::valueOf).toList();
        return new FeedPlaceResponse(place, postIds, postIds.size());
    }
}
