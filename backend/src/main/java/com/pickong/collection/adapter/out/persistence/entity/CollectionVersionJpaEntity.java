package com.pickong.collection.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * collection_versions 테이블 JPA 엔티티.
 * 컬렉션의 버전(스냅샷) 메타데이터를 저장한다.
 * ⚠ 추가 전용(append-only). 수정·삭제 없음.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "collection_versions")
public class CollectionVersionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_id", nullable = false)
    private Long collectionId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "place_count", nullable = false)
    private int placeCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
