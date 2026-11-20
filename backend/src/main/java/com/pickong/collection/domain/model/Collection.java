package com.pickong.collection.domain.model;

import com.pickong.common.domain.Visibility;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 컬렉션 애그리거트 루트. 제목 · 설명 · 공개 범위 · 핀 · 노출 · 표지 · 가격과 구성 장소(CollectionPlace) 목록.
 * <p>price가 null이면 무료입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Collection {
    private Long id;
    private UUID publicId;
    private Long userId;
    private String title;
    private String description;
    private Visibility visibility;
    private boolean listed;
    private boolean pinned;
    private String pinStyle;
    private String coverImageUrl;
    private Integer price;
    private int placeCount;
    private OffsetDateTime hiddenAt;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Builder.Default
    private List<CollectionPlace> places = new ArrayList<>();

    public List<CollectionPlace> getPlaces() {
        return List.copyOf(places);
    }
}
