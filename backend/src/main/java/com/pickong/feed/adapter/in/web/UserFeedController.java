package com.pickong.feed.adapter.in.web;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.feed.adapter.in.web.response.FeedPageResponse;
import com.pickong.feed.adapter.in.web.response.FeedResponse;
import com.pickong.feed.application.dto.FeedPage;
import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.application.port.in.FeedViewUseCase;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /users/{username}/feeds — 특정 사용자의 피드 목록.
 * FeedController가 /feeds에 묶여 있어 별도 컨트롤러로 분리함.
 */
@Validated
@RestController
public class UserFeedController {

    private final FeedUseCase feedUseCase;
    private final FeedViewUseCase feedViewUseCase;

    public UserFeedController(FeedUseCase feedUseCase, FeedViewUseCase feedViewUseCase) {
        this.feedUseCase = feedUseCase;
        this.feedViewUseCase = feedViewUseCase;
    }

    // 비로그인 또는 타인 → PUBLIC만 반환
    // 본인 → 모든 visibility(PUBLIC + FOLLOWERS + PRIVATE) 반환
    @GetMapping("/users/{username}/feeds")
    public FeedPageResponse getUserFeeds(
            @PathVariable String username,
            @CurrentUser(required = false) LoginUser viewer,
            @RequestParam(required = false) Long cursor,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        Long viewerUserId = viewer != null ? viewer.userId() : null;
        FeedPage page = feedUseCase.getUserFeeds(username, viewerUserId, cursor, limit);
        return FeedPageResponse.of(
                feedViewUseCase.viewsOf(page.getFeeds(), viewerUserId).stream().map(FeedResponse::from).toList(),
                page.getNextCursor(),
                page.isHasNext());
    }
}
