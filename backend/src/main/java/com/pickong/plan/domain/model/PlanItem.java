package com.pickong.plan.domain.model;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 일정의 방문 장소. Plan 애그리거트 안에서만 다룹니다. 같은 일정에 같은 장소는 1번만 들어갑니다.
 * <p>fixedTime이 null이면 유동 장소, extra는 일정 외 방문입니다. reservation은 0..1개입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlanItem {
    private Long id;
    private Long placeId;
    private int sortOrder;
    private LocalTime fixedTime;
    private boolean visited;
    private OffsetDateTime visitedAt;
    private boolean extra;
    private Reservation reservation;
}
