package com.pickong.feed.adapter.in.web.response;

import com.pickong.common.domain.Visibility;
import com.pickong.feed.application.dto.FeedAuthor;
import com.pickong.feed.application.dto.FeedView;
import com.pickong.feed.application.dto.FeedViewPlace;
import com.pickong.feed.domain.model.Feed;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 게시물 응답. 프론트 {@code entities/post/api/dto.ts}의 PostDto 모양
 * 내부 순번 작성자 id(userId)는 내보내지 않고 author.id(users.public_id)만 내보냄
 *
 * @param id     게시물 id. 경로가 아직 숫자 id를 받아서 숫자 id의 문자열 (public_id로 바꾸는 작업은 #200)
 * @param photos 사진 순서대로. 사진별 장소는 아직 저장하지 않아서, 장소가 1개면 모든 사진에 그 장소를 붙이고 여러 개면 null (#155)
 */
public record FeedResponse(
        String id,
        Author author,
        List<Photo> photos,
        List<Place> places,
        String content,
        Visibility visibility,
        long likeCount,
        long commentCount,
        boolean isLiked,
        boolean isBookmarked,
        boolean sponsored,
        String sponsorName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /** 작성자 요약. 프론트 화면은 아이디 · 프로필 사진 · 지역만 씀 */
    public record Author(String id, String username, String nickname, String profileImageUrl, String region) {
        static Author from(FeedAuthor author) {
            return new Author(author.publicId(), author.username(), author.nickname(), author.profileImageUrl(), author.region());
        }
    }

    /** 사진 한 장 */
    public record Photo(String url, String placeId) {
    }

    /** 게시물에 연결된 장소. 프론트 PlaceDto에서 장소 상세에만 있는 값(영업시간, 대표 사진)은 null */
    public record Place(
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
        static Place from(FeedViewPlace place) {
            // 사용자가 지도에서 직접 등록한 장소(CUSTOM)만 isCustom
            return new Place(place.publicId(), place.name(), place.address(), place.region(), place.lat(), place.lng(),
                    place.category(), "CUSTOM".equals(place.sourceType()), place.visibility(), place.description(), null, null);
        }
    }

    public static FeedResponse from(FeedView view) {
        Feed feed = view.feed();
        List<Place> places = view.places().stream().map(Place::from).toList();
        // 장소가 정확히 1개일 때만 모든 사진에 붙임 (여러 개면 어느 사진의 장소인지 알 수 없음)
        String photoPlaceId = places.size() == 1 ? places.get(0).id() : null;
        List<Photo> photos = view.photoUrls().stream().map(url -> new Photo(url, photoPlaceId)).toList();
        return new FeedResponse(
                String.valueOf(feed.getId()),
                Author.from(view.author()),
                photos,
                places,
                feed.getContent(),
                feed.getVisibility(),
                feed.getLikeCount(),
                feed.getCommentCount(),
                view.liked(),
                view.bookmarked(),
                feed.isSponsored(),
                feed.getSponsorName(),
                feed.getCreatedAt(),
                feed.getUpdatedAt());
    }
}
