package com.pickong.feed.domain.model;

import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 댓글 애그리거트. 게시물과 별도 애그리거트이며 feedId로만 참조합니다.
 * parentId가 있으면 대댓글입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Comment {
    private Long id;
    private Long feedId;
    private Long userId;
    private Long parentId;
    private String content;
    private long likeCount;
    private OffsetDateTime hiddenAt;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public boolean isReply() {
        return parentId != null;
    }
}
