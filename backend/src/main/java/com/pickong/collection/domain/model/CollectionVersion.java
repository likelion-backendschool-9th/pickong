package com.pickong.collection.domain.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 컬렉션 버전 애그리거트 (Collection과 별도). 구매 시점의 스냅샷이며 추가만 하고 수정 · 삭제하지 않습니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionVersion {
    private Long id;
    private Long collectionId;
    private int versionNo;
    private String title;
    private String description;
    private int placeCount;
    private OffsetDateTime createdAt;
    @Builder.Default
    private List<CollectionVersionPlace> places = new ArrayList<>();

    public List<CollectionVersionPlace> getPlaces() {
        return List.copyOf(places);
    }
}
