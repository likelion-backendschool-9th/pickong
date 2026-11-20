package com.pickong.savedplace.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * saved_place_tags 테이블 JPA 엔티티.
 * 저장 장소에 붙인 사용자 정의 태그를 저장한다.
 * ⚠ 태그 마스터 테이블 참조 여부 팀 확인 필요.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "saved_place_tags")
public class SavedPlaceTagJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saved_place_id", nullable = false)
    private Long savedPlaceId;

    @Column(name = "tag", nullable = false)
    private String tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
