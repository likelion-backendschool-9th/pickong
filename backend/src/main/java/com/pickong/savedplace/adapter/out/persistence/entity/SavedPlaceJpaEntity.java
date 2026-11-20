package com.pickong.savedplace.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * saved_places 테이블 JPA 엔티티.
 * 사용자가 저장한 개인 장소를 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "saved_places")
public class SavedPlaceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    /** 방문 상태. 값 목록: WANT_TO_GO, VISITED, NOT_INTERESTED */
    @Column(name = "visit_status", nullable = false, length = 20)
    private String visitStatus;

    @Column(name = "visited_at")
    private OffsetDateTime visitedAt;

    /** 저장 출처. 값 목록: FEED, COLLECTION_SNAPSHOT, AI_IMPORT, DIRECT 등 */
    @Column(name = "source", length = 30)
    private String source;

    /** source가 FEED일 때 원본 피드 id. */
    @Column(name = "source_feed_id")
    private Long sourceFeedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
