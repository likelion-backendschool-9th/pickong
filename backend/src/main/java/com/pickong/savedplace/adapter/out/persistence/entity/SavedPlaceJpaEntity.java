package com.pickong.savedplace.adapter.out.persistence.entity;

import com.pickong.savedplace.domain.model.SavedPlace;
import com.pickong.savedplace.domain.vo.SavedPlaceSource;
import com.pickong.savedplace.domain.vo.VisitStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * saved_places 테이블 JPA 엔티티 (V17 + V19, ERD 기준).
 * 사용자가 저장한 개인 장소를 저장한다. 출처 컬럼은 처음 저장할 때만 쓰고 바꾸지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "saved_places")
public class SavedPlaceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false, updatable = false)
    private Long placeId;

    @Column(name = "memo", length = 500)
    private String memo;

    /** 방문 상태. 값 목록: WANT_TO_GO, VISITED */
    @Column(name = "visit_status", nullable = false, length = 10)
    private String visitStatus;

    @Column(name = "visited_at")
    private OffsetDateTime visitedAt;

    /** 저장 출처. 값 목록: FEED, COLLECTION, LINK_IMPORT, SEARCH, CUSTOM, PLAN_VISIT */
    @Column(name = "source_type", nullable = false, length = 15, updatable = false)
    private String sourceType;

    /** source_type이 FEED일 때 원본 피드 id. */
    @Column(name = "source_feed_id", updatable = false)
    private Long sourceFeedId;

    /** source_type이 COLLECTION일 때 원본 컬렉션 id. */
    @Column(name = "source_collection_id", updatable = false)
    private Long sourceCollectionId;

    @Column(name = "source_label", length = 100, updatable = false)
    private String sourceLabel;

    /** source_type이 LINK_IMPORT일 때 원문 URL. */
    @Column(name = "source_url", updatable = false)
    private String sourceUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public SavedPlace toDomain() {
        return SavedPlace.builder()
                .id(id)
                .userId(userId)
                .placeId(placeId)
                .memo(memo)
                .visitStatus(VisitStatus.valueOf(visitStatus))
                .visitedAt(visitedAt)
                .sourceType(SavedPlaceSource.valueOf(sourceType))
                .sourceFeedId(sourceFeedId)
                .sourceCollectionId(sourceCollectionId)
                .sourceLabel(sourceLabel)
                .sourceUrl(sourceUrl)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /** 바꿀 수 있는 값(방문 상태 · 메모)만 도메인에서 가져옵니다. */
    public void apply(SavedPlace savedPlace) {
        this.memo = savedPlace.getMemo();
        this.visitStatus = savedPlace.getVisitStatus().name();
        this.visitedAt = savedPlace.getVisitedAt();
        this.updatedAt = savedPlace.getUpdatedAt();
    }
}
