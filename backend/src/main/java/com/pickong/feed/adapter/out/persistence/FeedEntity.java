package com.pickong.feed.adapter.out.persistence;

import com.pickong.feed.domain.Feed;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

@Getter
@Entity
@Table(name = "feeds")
public class FeedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long placeId;

    private Long authorId;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String imageUrls;

    public static FeedEntity from(Feed feed) {
        FeedEntity entity = new FeedEntity();
        entity.placeId = feed.getPlaceId();
        entity.authorId = feed.getAuthorId();
        entity.content = feed.getContent();
        entity.createdAt = feed.getCreatedAt();
        entity.imageUrls = feed.getImageUrls() != null ? String.join(",", feed.getImageUrls()) : null;
        entity.updatedAt = feed.getUpdatedAt();
        return entity;
    }

    public Feed toDomain() {
        Feed feed = Feed.create(this.placeId, this.authorId, this.content,
                this.imageUrls != null ? List.of(this.imageUrls.split(",")) : List.of());
        return feed;
    }
}
