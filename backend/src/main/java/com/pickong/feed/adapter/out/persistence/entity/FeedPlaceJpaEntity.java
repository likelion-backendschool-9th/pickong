package com.pickong.feed.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.pickong.feed.domain.model.FeedPlace;
import java.time.OffsetDateTime;

/**
 * feed_places 테이블 JPA 엔티티.
 * 피드에 태그된 장소 목록을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "feed_places")
public class FeedPlaceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feed_id", nullable = false)
    private Long feedId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 태그 장소 목록은 게시물 저장 때마다 지우고 다시 넣으므로 id를 넘기지 않습니다. */
    public static FeedPlaceJpaEntity of(Long feedId, FeedPlace place) {
        FeedPlaceJpaEntity entity = new FeedPlaceJpaEntity();
        entity.feedId = feedId;
        entity.placeId = place.getPlaceId();
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public FeedPlace toDomain() {
        return FeedPlace.builder().id(id).placeId(placeId).build();
    }
}
