package com.pickong.feed.adapter.in.web;

import com.pickong.feed.application.port.in.FeedUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.feed.adapter.in.web.request.CreateFeedRequest;
import com.pickong.feed.adapter.in.web.request.UpdateFeedRequest;
import com.pickong.feed.adapter.in.web.response.FeedPageResponse;
import com.pickong.feed.adapter.in.web.response.FeedResponse;
import com.pickong.feed.application.dto.FeedPage;

@Validated
@RestController
@RequestMapping("/feeds")
public class FeedController {

    private final FeedUseCase feedUseCase;

    public FeedController(FeedUseCase feedUseCase) {
        this.feedUseCase = feedUseCase;
    }

    // ----목록 조회는 임의로 최대 50까지 허용(필요 시 @Max()에서 수정 해주세요----

    // 단건 상세조회
    @GetMapping("/{feedId}")
    public FeedResponse getFeed(@PathVariable @Positive Long feedId) {
        return FeedResponse.from(feedUseCase.getFeed(feedId));
    }

    // 피드 목록 조회. tab=recommended(추천) | tab=following(팔로잉). limit은 1~50 사이만 허용
    // cursor: 이전 응답의 nextCursor 값 (첫 요청 시 생략)
    // 비로그인 허용 — recommended는 userId 없이도 조회 가능, following은 null이면 빈 피드 반환
    @GetMapping
    public FeedPageResponse getFeeds(
            @RequestParam @NotNull String tab,
            @CurrentUser LoginUser user,
            @RequestParam(required = false) Long cursor,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        Long userId = user.userId();
        FeedPage page = tab.equals("following")
                ? feedUseCase.getFollowingFeeds(userId, cursor, limit)
                : feedUseCase.getRecommendedFeeds(userId, cursor, limit);
        return FeedPageResponse.of(
                page.getFeeds().stream().map(FeedResponse::from).toList(),
                page.getNextCursor(),
                page.isHasNext());
    }

    // 피드 지도 장소 목록 조회. tab=recommended(추천) | tab=following(팔로잉)
    // TODO: 피드 장소 조회 UseCase 구현 후 연결 필요
    @GetMapping("/places")
    public FeedPageResponse getFeedPlaces(
            @RequestParam @NotNull String tab,
            @CurrentUser LoginUser user,
            @RequestParam(required = false) Long cursor,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        Long userId = user.userId();
        FeedPage page = tab.equals("following")
                ? feedUseCase.getFollowingFeeds(userId, cursor, limit)
                : feedUseCase.getRecommendedFeeds(userId, cursor, limit);
        return FeedPageResponse.of(
                page.getFeeds().stream().map(FeedResponse::from).toList(),
                page.getNextCursor(),
                page.isHasNext());
    }

    // 피드 생성
    @PostMapping
    public FeedResponse createFeed(@Valid @RequestBody CreateFeedRequest request) {
        return FeedResponse.from(feedUseCase.createFeed(request.toCommand()));
    }

    // 피드 수정
    @PutMapping("/{feedId}")
    public FeedResponse updateFeed(
            @PathVariable @Positive Long feedId,
            @Valid @RequestBody UpdateFeedRequest request) {
        return FeedResponse.from(feedUseCase.updateFeed(feedId, request.toCommand()));

    }

    // 피드 삭제
    @DeleteMapping("/{feedId}")
    public void deleteFeed(@PathVariable @Positive Long feedId) {
        feedUseCase.deleteFeed(feedId);
    }

}
