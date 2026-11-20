package com.pickong.feed.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

@Getter
public class Feed {
    private Long id;
    private Long placeId;
    private Long authorId;
    private String content;
    private List<String> comments;
    private List<String> tags;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Feed create(Long placeId, Long authorId, String content, List<String> imageUrls) {
        Feed feed = new Feed();
        feed.placeId = placeId;
        feed.authorId = authorId;
        feed.content = content;
        feed.imageUrls = imageUrls;
        feed.createdAt = LocalDateTime.now();
        return feed;
    }

    public void update(Long placeId, String content, List<String> imageUrls) {
        this.placeId = placeId;
        this.content = content;
        this.imageUrls = imageUrls;
        this.updatedAt = LocalDateTime.now();
    }
}
