package com.pickong.place.domain.model;

import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 특정 날짜의 영업시간 예외(임시 휴무 · 연장 등). Place 애그리거트 안에서만 다룹니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HoursException {
    private Long id;
    private LocalDate exceptionDate;
    private boolean closed;
    private Integer openTime;
    private Integer closeTime;
    private String note;
}
