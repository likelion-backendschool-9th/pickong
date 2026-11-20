package com.pickong.savedplace.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * saved_place_photos 테이블 JPA 엔티티.
 * 저장 장소에 첨부된 사진 목록을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "saved_place_photos")
public class SavedPlacePhotosJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saved_place_id", nullable = false)
    private Long savedPlaceId;

    @Column(name = "media_file_id", nullable = false)
    private Long mediaFileId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
