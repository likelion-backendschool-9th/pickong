package com.pickong.feed.adapter.in.web.response;

import com.pickong.common.domain.Visibility;
import com.pickong.feed.domain.model.Feed;
import com.pickong.feed.domain.model.FeedPhoto;
import com.pickong.feed.domain.model.FeedPlace;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class FeedResponse {
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
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<FeedPhoto> photos = new ArrayList<>();
    private List<FeedPlace> places = new ArrayList<>();

    public static FeedResponse from(Feed feed) {
        FeedResponse response = new FeedResponse();
        response.id = feed.getId();
        response.publicId = feed.getPublicId();
        response.userId = feed.getUserId();
        response.content = feed.getContent();
        response.visibility = feed.getVisibility();
        response.likeCount = feed.getLikeCount();
        response.commentCount = feed.getCommentCount();
        response.bookmarkCount = feed.getBookmarkCount();
        response.sponsored = feed.isSponsored();
        response.createdAt = feed.getCreatedAt();
        response.updatedAt = feed.getUpdatedAt();
        response.photos = feed.getPhotos();
        response.places = feed.getPlaces();
        return response;
    }

}
