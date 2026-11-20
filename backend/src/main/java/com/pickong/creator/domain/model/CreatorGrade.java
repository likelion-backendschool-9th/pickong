package com.pickong.creator.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 크리에이터 등급과 수수료율(0.30~0.35).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CreatorGrade {
    private Long id;
    private String name;
    private BigDecimal feeRate;
    private OffsetDateTime createdAt;
}
