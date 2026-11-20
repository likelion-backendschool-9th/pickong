package com.pickong.place.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 요일별 영업시간. Place 애그리거트 안에서만 다룹니다.
 * <p>dayOfWeek는 0=일 ~ 6=토, 시각은 0시 기준 분이며 1440을 넘으면 다음날입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BusinessHours {
    private Long id;
    private int dayOfWeek;
    private int openTime;
    private int closeTime;
    private boolean closed;
}
