package com.pickong.feed.application.dto;

import com.pickong.common.domain.Visibility;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 관리자 게시물 목록의 읽기 모델. 공개 ID와 표시 데이터만 전달합니다. */
public record AdminFeedView(
        UUID publicId,
        String title,
        Author author,
        Visibility visibility,
        boolean hidden,
        long reportCount,
        OffsetDateTime createdAt) {

    public record Author(long id, String username, String nickname, String profileImageUrl) {
    }
}
