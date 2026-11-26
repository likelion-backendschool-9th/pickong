package com.pickong.feed.adapter.in.web.admin;

import com.pickong.common.domain.Visibility;
import com.pickong.feed.application.dto.AdminFeedView;
import java.time.OffsetDateTime;

/**
 * AdminContent의 게시물 표현. id는 공개 UUID이고, 작성자의 내부 id는 문자열입니다.
 */
public record AdminFeedResponse(
        String id, String type, String title, AuthorResponse author, String imageUrl,
        Visibility visibility, Long price, boolean hidden, long reportCount, OffsetDateTime createdAt, String link) {

    public static AdminFeedResponse from(AdminFeedView feed) {
        AdminFeedView.Author author = feed.author();
        AuthorResponse writer = author == null ? null : new AuthorResponse(Long.toString(author.id()),
                author.username(), author.nickname(), author.profileImageUrl());
        // 미디어 조회와 회원 상세 화면의 실제 연동이 완료될 때까지 URL은 만들지 않습니다.
        return new AdminFeedResponse(feed.publicId().toString(), "POST", feed.title(), writer, null,
                feed.visibility(), null, feed.hidden(), feed.reportCount(), feed.createdAt(), null);
    }

    public record AuthorResponse(String id, String username, String nickname, String profileImageUrl) {
    }
}
