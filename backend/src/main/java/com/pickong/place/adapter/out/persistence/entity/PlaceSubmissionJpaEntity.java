package com.pickong.place.adapter.out.persistence.entity;

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
 * place_submissions 테이블.
 * <p>
 * location은 geography(Point, 4326)입니다. Hibernate Spatial이 아직 없어 이 필드에 매핑하지 않고,
 * {@code PlaceSubmissionPersistenceAdapter}가 위도 · 경도로 읽고 씁니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "place_submissions")
public class PlaceSubmissionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submitter_user_id", nullable = false)
    private Long submitterUserId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "reviewer_admin_id")
    private Long reviewerAdminId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "place_id")
    private Long placeId;
}
