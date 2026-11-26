package com.pickong.feed.domain.model;

import java.time.OffsetDateTime;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
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
    // DB에 저장되지 않는 집계 필드 — 목록 조회 시 서브쿼리로 계산됨
    private long replyCount;
    private OffsetDateTime hiddenAt;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static Comment create(Long feedId, Long userId, Long parentId, String content) {
        return Comment.builder()
                .feedId(feedId)
                .userId(userId)
                .parentId(parentId)
                .content(content)
                .likeCount(0)
                .build();
    }

    public void update(String content) {
        this.content = content;
    }

    public boolean isReply() {
        return parentId != null;
    }

    public void ensureReadable(Long viewerId, boolean admin) {
        if (hiddenAt != null && !admin && !userId.equals(viewerId)) {
            throw new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND);
        }
    }

    public boolean changeHidden(boolean hidden, OffsetDateTime now) {
        if (deletedAt != null) throw new BusinessException(FeedErrorCode.COMMENT_NOT_FOUND);
        if ((hiddenAt != null) == hidden) return false;
        hiddenAt = hidden ? now : null;
        updatedAt = now;
        return true;
    }
}
