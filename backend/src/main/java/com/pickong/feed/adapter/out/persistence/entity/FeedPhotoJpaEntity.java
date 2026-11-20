package com.pickong.feed.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.pickong.feed.domain.model.FeedPhoto;
import java.time.OffsetDateTime;

/**
 * feed_photos 테이블 JPA 엔티티.
 * 피드에 첨부된 사진 목록을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "feed_photos")
public class FeedPhotoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feed_id", nullable = false)
    private Long feedId;

    @Column(name = "media_file_id", nullable = false)
    private Long mediaFileId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 사진 목록은 게시물 저장 때마다 지우고 다시 넣으므로 id를 넘기지 않습니다. */
    public static FeedPhotoJpaEntity of(Long feedId, FeedPhoto photo) {
        FeedPhotoJpaEntity entity = new FeedPhotoJpaEntity();
        entity.feedId = feedId;
        entity.mediaFileId = photo.getMediaFileId();
        entity.sortOrder = photo.getSortOrder();
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public FeedPhoto toDomain() {
        return FeedPhoto.builder().id(id).mediaFileId(mediaFileId).sortOrder(sortOrder).build();
    }
}
