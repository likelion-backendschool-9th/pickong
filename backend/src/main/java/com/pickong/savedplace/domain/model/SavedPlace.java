package com.pickong.savedplace.domain.model;

import com.pickong.savedplace.domain.vo.SavedPlaceSource;
import com.pickong.savedplace.domain.vo.VisitStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 저장 장소 애그리거트 루트. 회원당 장소 1번, 방문 상태, 메모, 출처와 개인 사진 · 태그.
 * <p>sourceFeedId는 source가 FEED일 때만 있습니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SavedPlace {
    private Long id;
    private Long userId;
    private Long placeId;
    private String memo;
    private VisitStatus visitStatus;
    private OffsetDateTime visitedAt;
    private SavedPlaceSource source;
    private Long sourceFeedId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Builder.Default
    private List<SavedPlacePhoto> photos = new ArrayList<>();
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    public List<SavedPlacePhoto> getPhotos() {
        return List.copyOf(photos);
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }
}
