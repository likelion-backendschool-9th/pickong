package com.pickong.place.domain.model;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.domain.Visibility;
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
    private PlaceSourceType sourceType;
    /** PUBLIC · PRIVATE만 씁니다. 직접 만든 장소(CUSTOM)는 사용자가 고른 값입니다. */
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;
    private String name;
    private String address;
    private String region;
    @Builder.Default
    private String countryCode = "KR";
    private GeoPoint location;
    @Builder.Default
    private String category = "ETC";
    private String description;
    private String phone;
    private String imageUrl;
    private String hoursSource;
    private OffsetDateTime hoursVerifiedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
    /** 직접 만든 장소(CUSTOM)의 등록자. 그 외에는 null */
    private Long ownerId;
    private Long imageMediaId;
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
