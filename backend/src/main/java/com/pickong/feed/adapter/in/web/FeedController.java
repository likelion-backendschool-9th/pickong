package com.pickong.feed.adapter.in.web;

import com.pickong.feed.application.port.in.FeedUseCase;
import com.pickong.feed.domain.model.Feed;

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
    public Feed getFeed(@PathVariable @Positive Long feedId) {
        return feedUseCase.getFeed(feedId);
    }

    // 최신 피드 목록 조회. limit은 1~50 사이만 허용
    @GetMapping
    public List<Feed> getLatestFeeds(
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        return feedUseCase.getLatestFeeds(limit);
    }

    // 추천 피드 목록 조회. limit은 1~50 사이만 허용
    @GetMapping("/recommended")
    public List<Feed> getRecommendedFeeds(
            @RequestParam @NotNull Long userId,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        return feedUseCase.getRecommendedFeeds(userId, limit);
    }

    // 팔로잉 피드 목록 조회. limit은 1~50 사이만 허용
    @GetMapping("/following")
    public List<Feed> getFollowingFeeds(
            @RequestParam @NotNull Long userId,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        return feedUseCase.getFollowingFeeds(userId, limit);
    }

    // 태그 기반 피드 목록 조회. limit은 1~50 사이만 허용
    @GetMapping("/tag")
    public List<Feed> getFeedsByTag(
            @RequestParam @NotNull Long userId,
            @RequestParam List<String> tags,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        return feedUseCase.getFeedsByTag(userId, tags, limit);
    }

    // 피드 생성
    @PostMapping
    public Feed createFeed(@RequestBody CreateFeedRequest request) {
        return feedUseCase.createFeed(request.toCommand());
    }

    // 피드 수정
    @PutMapping("/{feedId}")
    public Feed updateFeed(
            @PathVariable @Positive Long feedId,
            @RequestBody UpdateFeedRequest request) {
        return feedUseCase.updateFeed(feedId, request.toCommand());
    }

    // 피드 삭제
    @DeleteMapping("/{feedId}")
    public void deleteFeed(@PathVariable @Positive Long feedId) {
        feedUseCase.deleteFeed(feedId);
    }

}
