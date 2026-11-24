package com.pickong.place.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * places 테이블 JPA 엔티티.
 * 장소 마스터 데이터를 저장한다.
 * location 컬럼(GEOGRAPHY)은 Hibernate Spatial을 통해 매핑한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "places")
public class PlaceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    /**
     * 좌표. GEOGRAPHY(Point, 4326).
     * Hibernate Spatial 의존성이 있어야 하며, org.locationtech.jts.geom.Point 사용.
     * ⚠ 의존성 추가 전까지는 String으로 임시 보관.
     */
    @Column(name = "location", nullable = false, columnDefinition = "geography(Point,4326)")
    private String location;

    /** 장소 카테고리. 값 목록 팀 확인 필요 (PlaceCategory VO). */
    @Column(name = "category")
    private String category;

    /** 장소 출처. 값 목록: KAKAO, CUSTOM, AI_EXTRACTED 등 */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "phone")
    private String phone;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
