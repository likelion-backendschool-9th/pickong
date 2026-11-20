package com.pickong.collection.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * collection_version_places 테이블 JPA 엔티티.
 * 특정 버전의 컬렉션에 포함된 장소 스냅샷을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "collection_version_places")
public class CollectionVersionPlaceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_version_id", nullable = false)
    private Long collectionVersionId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 스냅샷 시점의 장소 이름. */
    @Column(name = "name", nullable = false)
    private String name;

    /** 스냅샷 시점의 주소. */
    @Column(name = "address")
    private String address;

    /**
     * 스냅샷 시점의 좌표. GEOGRAPHY(Point, 4326).
     * ⚠ Hibernate Spatial 의존성 추가 전까지는 VARCHAR로 임시 보관.
     */
    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private String location;

    /** 스냅샷 시점의 카테고리. */
    @Column(name = "category")
    private String category;

    /** 영업시간·전화·웹 등 비정형 스냅샷. JSONB → String 저장. */
    @Column(name = "detail_snapshot", columnDefinition = "TEXT")
    private String detailSnapshot;
}
