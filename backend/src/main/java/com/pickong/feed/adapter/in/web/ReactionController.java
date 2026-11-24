package com.pickong.feed.adapter.in.web;

import com.pickong.feed.application.port.in.FeedReactionUseCase;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/feeds/{feedId}")
public class ReactionController {

    private final FeedReactionUseCase feedReactionUseCase;

    public ReactionController(FeedReactionUseCase feedReactionUseCase) {
        this.feedReactionUseCase = feedReactionUseCase;
    }

    // 좋아요 추가 — 201 Created
    @PostMapping("/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public void likeFeed(
            @PathVariable @Positive Long feedId,
            @RequestParam @Positive Long userId) {
        // TODO(JWT): JWT 도입 후 @RequestParam userId 제거 — SecurityContext에서 인증된 userId 사용
        feedReactionUseCase.likeFeed(feedId, userId);
    }

    // 좋아요 취소 — 204 No Content
    @DeleteMapping("/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeFeed(
            @PathVariable @Positive Long feedId,
            @RequestParam @Positive Long userId) {
        feedReactionUseCase.unlikeFeed(feedId, userId);
    }

    // 북마크 추가 — 201 Created
    @PostMapping("/bookmarks")
    @ResponseStatus(HttpStatus.CREATED)
    public void bookmarkFeed(
            @PathVariable @Positive Long feedId,
            @RequestParam @Positive Long userId) {
        feedReactionUseCase.bookmarkFeed(feedId, userId);
    }

    // 북마크 취소 — 204 No Content
    @DeleteMapping("/bookmarks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbookmarkFeed(
            @PathVariable @Positive Long feedId,
            @RequestParam @Positive Long userId) {
        feedReactionUseCase.unbookmarkFeed(feedId, userId);
    }
}
