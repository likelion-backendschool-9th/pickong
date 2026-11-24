package com.pickong.place.domain.model;

import com.pickong.common.domain.GeoPoint;
import com.pickong.place.domain.vo.PlaceSourceType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 장소 애그리거트 루트. 요일별 영업시간(BusinessHours)과 날짜별 예외(HoursException)를 함께 다룹니다.
 * <p>category 값 목록은 확정 전이라 문자열로 둡니다 (backend_blueprint.md 8.7 #2).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Place {
    private Long id;
    private UUID publicId;
    private String name;
    private String address;
    private GeoPoint location;
    private String category;
    private PlaceSourceType sourceType;
    private String externalId;
    private String phone;
    private String websiteUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Builder.Default
    private List<BusinessHours> businessHours = new ArrayList<>();
    @Builder.Default
    private List<HoursException> hoursExceptions = new ArrayList<>();

    public List<BusinessHours> getBusinessHours() {
        return List.copyOf(businessHours);
    }

    public List<HoursException> getHoursExceptions() {
        return List.copyOf(hoursExceptions);
    }
}
