package com.pickong.feed.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.pickong.common.domain.Visibility;
import com.pickong.feed.domain.model.Feed;
import com.pickong.feed.domain.model.FeedPhoto;
import com.pickong.feed.domain.model.FeedPlace;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * feeds 테이블 JPA 엔티티.
 * 소셜 피드 게시물을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "feeds")
public class FeedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 공개 범위. 값 목록: PUBLIC, FOLLOWERS, PRIVATE */
    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "comment_count", nullable = false)
    private long commentCount;

    @Column(name = "bookmark_count", nullable = false)
    private long bookmarkCount;

    @Column(name = "is_sponsored", nullable = false)
    private boolean isSponsored;

    @Column(name = "sponsor_name", length = 100)
    private String sponsorName;

    /** 관리자 숨김 처리 일시. */
    @Column(name = "hidden_at")
    private OffsetDateTime hiddenAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static FeedJpaEntity from(Feed feed) {
        OffsetDateTime now = OffsetDateTime.now();
        FeedJpaEntity entity = new FeedJpaEntity();
        entity.id = feed.getId();
        entity.publicId = feed.getPublicId();
        entity.userId = feed.getUserId();
        entity.content = feed.getContent();
        entity.visibility = feed.getVisibility().name();
        entity.likeCount = feed.getLikeCount();
        entity.commentCount = feed.getCommentCount();
        entity.bookmarkCount = feed.getBookmarkCount();
        entity.isSponsored = feed.isSponsored();
        entity.sponsorName = feed.getSponsorName();
        entity.hiddenAt = feed.getHiddenAt();
        entity.deletedAt = feed.getDeletedAt();
        entity.createdAt = feed.getCreatedAt() != null ? feed.getCreatedAt() : now;
        entity.updatedAt = now;
        return entity;
    }

    public Feed toDomain(List<FeedPhoto> photos, List<FeedPlace> places) {
        return Feed.builder()
                .id(id)
                .publicId(publicId)
                .userId(userId)
                .content(content)
                .visibility(Visibility.valueOf(visibility))
                .likeCount(likeCount)
                .commentCount(commentCount)
                .bookmarkCount(bookmarkCount)
                .sponsored(isSponsored)
                .sponsorName(sponsorName)
                .hiddenAt(hiddenAt)
                .deletedAt(deletedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .photos(photos)
                .places(places)
                .build();
    }
}
