package com.pickong.place.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * place_hours_exceptions 테이블 JPA 엔티티.
 * 장소의 특정 날짜 영업 시간 예외(임시 휴무·연장 등)를 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "place_hours_exceptions")
public class PlaceHoursExceptionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Column(name = "is_closed", nullable = false)
    private boolean isClosed;

    @Column(name = "open_time")
    private Integer openTime;

    @Column(name = "close_time")
    private Integer closeTime;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
