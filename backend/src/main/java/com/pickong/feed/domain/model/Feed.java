package com.pickong.feed.domain.model;

import com.pickong.common.domain.Visibility;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 게시물 애그리거트 루트. 사진(FeedPhoto)과 태그 장소(FeedPlace)를 함께 다룹니다.
 * 댓글(Comment)과 좋아요 · 북마크는 별도 애그리거트입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Feed {
    private Long id;
    private UUID publicId;
    private Long userId;
    private String content;
    private Visibility visibility;
    private long likeCount;
    private long commentCount;
    private long bookmarkCount;
    private boolean sponsored;
    private String sponsorName;
    private OffsetDateTime hiddenAt;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Builder.Default
    private List<FeedPhoto> photos = new ArrayList<>();
    @Builder.Default
    private List<FeedPlace> places = new ArrayList<>();

    public static Feed create(Long userId, String content, Visibility visibility,
                              List<Long> mediaFileIds, List<Long> placeIds) {
        Feed feed = Feed.builder()
                .publicId(UUID.randomUUID())
                .userId(userId)
                .content(content)
                .visibility(visibility)
                .build();
        feed.replacePhotos(mediaFileIds);
        feed.replacePlaces(placeIds);
        return feed;
    }

    public void update(String content, Visibility visibility, List<Long> mediaFileIds, List<Long> placeIds) {
        this.content = content;
        this.visibility = visibility;
        replacePhotos(mediaFileIds);
        replacePlaces(placeIds);
    }

    public List<FeedPhoto> getPhotos() {
        return List.copyOf(photos);
    }

    public List<FeedPlace> getPlaces() {
        return List.copyOf(places);
    }

    private void replacePhotos(List<Long> mediaFileIds) {
        List<Long> ids = mediaFileIds == null ? List.of() : mediaFileIds;
        this.photos = new ArrayList<>(IntStream.range(0, ids.size())
                .mapToObj(i -> FeedPhoto.create(ids.get(i), i))
                .toList());
    }

    private void replacePlaces(List<Long> placeIds) {
        List<Long> ids = placeIds == null ? List.of() : placeIds;
        this.places = new ArrayList<>(ids.stream().distinct().map(FeedPlace::create).toList());
    }
}
