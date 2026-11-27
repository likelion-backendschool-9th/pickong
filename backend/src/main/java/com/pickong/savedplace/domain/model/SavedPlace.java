package com.pickong.savedplace.domain.model;

import com.pickong.common.error.BusinessException;
import com.pickong.savedplace.domain.SavedPlaceErrorCode;
import com.pickong.savedplace.domain.vo.SavedPlaceOrigin;
import com.pickong.savedplace.domain.vo.SavedPlaceSource;
import com.pickong.savedplace.domain.vo.VisitStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 저장 장소 애그리거트 루트. 회원당 장소 1번, 방문 상태, 메모, 출처와 개인 사진 · 태그.
 * <p>출처(sourceType · sourceFeedId · sourceCollectionId · sourceLabel · sourceUrl)는 처음 저장할 때만 정하고 바꾸지 않습니다.
 * sourceFeedId는 FEED일 때만, sourceCollectionId는 COLLECTION일 때만, sourceUrl은 LINK_IMPORT일 때만 있습니다 (SavedPlaceOrigin).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SavedPlace {

    /** ERD saved_places.memo VARCHAR(500) */
    public static final int MAX_MEMO_LENGTH = 500;

    private Long id;
    private Long userId;
    private Long placeId;
    private String memo;
    private VisitStatus visitStatus;
    private OffsetDateTime visitedAt;
    private SavedPlaceSource sourceType;
    private Long sourceFeedId;
    private Long sourceCollectionId;
    private String sourceLabel;
    private String sourceUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Builder.Default
    private List<SavedPlacePhoto> photos = new ArrayList<>();

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /** 새로 저장(찜)합니다. 가고 싶은 곳 · 메모 없음으로 시작합니다. */
    public static SavedPlace create(long userId, long placeId, SavedPlaceOrigin origin, OffsetDateTime now) {
        SavedPlaceOrigin o = origin == null ? SavedPlaceOrigin.direct() : origin;
        return SavedPlace.builder()
                .userId(userId)
                .placeId(placeId)
                .visitStatus(VisitStatus.WANT_TO_GO)
                .sourceType(o.type())
                .sourceFeedId(o.feedId())
                .sourceCollectionId(o.collectionId())
                .sourceLabel(o.label())
                .sourceUrl(o.url())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** 방문 상태를 바꿉니다. VISITED면 방문 시각을 남기고, 가고 싶은 곳으로 되돌리면 지웁니다. 같은 값이면 그대로 둡니다. */
    public void changeVisitStatus(VisitStatus status, OffsetDateTime now) {
        Objects.requireNonNull(status, "status");
        if (status == visitStatus) {
            return;
        }
        visitStatus = status;
        visitedAt = status == VisitStatus.VISITED ? now : null;
        updatedAt = now;
    }

    /** 메모를 바꿉니다. 빈 문자열은 메모 없음(null)입니다. */
    public void changeMemo(String newMemo, OffsetDateTime now) {
        String normalized = newMemo == null || newMemo.isBlank() ? null : newMemo.strip();
        if (normalized != null && normalized.length() > MAX_MEMO_LENGTH) {
            throw new BusinessException(SavedPlaceErrorCode.MEMO_TOO_LONG);
        }
        if (Objects.equals(normalized, memo)) {
            return;
        }
        memo = normalized;
        updatedAt = now;
    }

    public List<SavedPlacePhoto> getPhotos() {
        return List.copyOf(photos);
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }
}
