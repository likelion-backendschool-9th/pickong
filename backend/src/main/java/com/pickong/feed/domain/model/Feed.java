package com.pickong.feed.domain.model;

import com.pickong.common.domain.Visibility;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
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

    /** 삭제 글과 권한 없는 숨김 글은 존재 여부를 구분하지 않습니다. */
    public void ensureReadable(Long viewerId, boolean admin) {
        if (deletedAt != null || (hiddenAt != null && !admin && !userId.equals(viewerId))) {
            throw new BusinessException(FeedErrorCode.FEED_NOT_FOUND);
        }
    }

    /** 작성자도 숨김 글에 새 댓글·좋아요·북마크를 추가할 수 없습니다. */
    public void ensureAcceptsInteraction() {
        if (deletedAt != null || hiddenAt != null) {
            throw new BusinessException(FeedErrorCode.FEED_NOT_FOUND);
        }
    }

    /** visibility와 반응 데이터는 건드리지 않습니다. 상태가 같으면 시각도 유지합니다. */
    public boolean changeHidden(boolean hidden, OffsetDateTime now) {
        if (deletedAt != null) throw new BusinessException(FeedErrorCode.FEED_NOT_FOUND);
        if ((hiddenAt != null) == hidden) return false;
        hiddenAt = hidden ? now : null;
        updatedAt = now;
        return true;
    }

    public static String normalizeModerationReason(String raw, boolean hiding) {
        String value = raw == null ? "" : raw;
        int start = 0;
        int end = value.length();
        while (start < end && reasonSpace(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (end > start && reasonSpace(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        value = value.substring(start, end);
        if (value.isEmpty()) {
            if (hiding) throw new BusinessException(FeedErrorCode.FEED_HIDE_REASON_REQUIRED);
            return null;
        }
        if (value.codePointCount(0, value.length()) > 200) {
            throw new BusinessException(FeedErrorCode.FEED_MODERATION_REASON_TOO_LONG);
        }
        return value;
    }

    private static boolean reasonSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85;
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
