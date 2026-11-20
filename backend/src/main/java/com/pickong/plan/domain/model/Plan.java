package com.pickong.plan.domain.model;

import com.pickong.plan.domain.vo.PlanStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 일정 애그리거트 루트. 소유자, 날짜, 상태와 방문 장소(PlanItem) 목록.
 * <p>companions는 동행자 정보(JSON), sourcePlanId는 복사 출처 일정입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Plan {
    private Long id;
    private UUID publicId;
    private Long userId;
    private String title;
    private LocalDate planDate;
    private PlanStatus status;
    private String companions;
    private Long sourcePlanId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Builder.Default
    private List<PlanItem> items = new ArrayList<>();

    public List<PlanItem> getItems() {
        return List.copyOf(items);
    }
}
