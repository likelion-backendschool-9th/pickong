package com.pickong.place.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * place_business_hours 테이블 JPA 엔티티.
 * 장소의 요일별 영업 시간을 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "place_business_hours")
public class PlaceBusinessHoursJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    /** 요일. 0=일~6=토 */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    /** 영업 시작 시간(분). 1440 초과 시 다음날. MinuteOfDay VO. */
    @Column(name = "open_time", nullable = false)
    private int openTime;

    /** 영업 종료 시간(분). */
    @Column(name = "close_time", nullable = false)
    private int closeTime;

    @Column(name = "is_closed", nullable = false)
    private boolean isClosed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
