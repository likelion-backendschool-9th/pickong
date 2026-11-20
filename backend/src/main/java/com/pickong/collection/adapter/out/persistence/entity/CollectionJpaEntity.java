package com.pickong.collection.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * collections 테이블 JPA 엔티티.
 * 사용자가 만든 장소 컬렉션을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "collections")
public class CollectionsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 공개 범위. 값 목록: PUBLIC, FOLLOWERS, PRIVATE */
    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility;

    /** 탐색 화면에 노출 여부. */
    @Column(name = "is_listed", nullable = false)
    private boolean isListed;

    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned;

    /** 핀 스타일. PinStyle VO. */
    @Column(name = "pin_style")
    private String pinStyle;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    /**
     * 가격. NULL = 무료. 500~50,000, 100원 단위.
     * CollectionPrice VO.
     */
    @Column(name = "price")
    private Integer price;

    @Column(name = "place_count", nullable = false)
    private int placeCount;

    /** 관리자 숨김 처리 일시. */
    @Column(name = "hidden_at")
    private OffsetDateTime hiddenAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
