package com.pickong.plan.domain.model;

import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 방문 장소의 예약. PlanItem당 0..1개이며 Plan 애그리거트 안에서만 다룹니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Reservation {
    private Long id;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer partySize;
    private String reservationRef;
}
