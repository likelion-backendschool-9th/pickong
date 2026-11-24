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

    /** 공개 범위. PUBLIC, PRIVATE */
    @Column(name = "visibility", nullable = false, length = 10)
    private String visibility;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    /**
     * 좌표. GEOGRAPHY(Point, 4326).
     * Hibernate Spatial 의존성이 있어야 하며, org.locationtech.jts.geom.Point 사용.
     * ⚠ 의존성 추가 전까지는 String으로 임시 보관.
     */
    @Column(name = "location", nullable = false, columnDefinition = "geography(Point,4326)")
    private String location;

    /** 장소 카테고리. 값 목록 팀 확인 필요 (PlaceCategory VO). */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    /** 장소 출처. 값 목록: KAKAO, CUSTOM, AI_EXTRACTED, SYSTEM */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "phone")
    private String phone;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "hours_source", length = 15)
    private String hoursSource;

    @Column(name = "hours_verified_at")
    private OffsetDateTime hoursVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /** 직접 만든 장소(CUSTOM)의 등록자. 그 외에는 null */
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "image_media_id")
    private Long imageMediaId;
}
