package com.pickong.feed.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 게시물에 첨부된 사진. Feed 애그리거트 안에서만 만들고 바꿉니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FeedPhoto {
    private Long id;
    private Long mediaFileId;
    private int sortOrder;

    static FeedPhoto create(Long mediaFileId, int sortOrder) {
        return FeedPhoto.builder().mediaFileId(mediaFileId).sortOrder(sortOrder).build();
    }
}
