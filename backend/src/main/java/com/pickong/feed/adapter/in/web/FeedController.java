package com.pickong.feed.adapter.in.web;

import com.pickong.feed.application.port.in.FeedUseCase;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import com.pickong.common.error.BusinessException;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.common.security.AdminPrincipal;
import org.springframework.security.core.Authentication;
import com.pickong.feed.adapter.in.web.request.CreateFeedRequest;
import com.pickong.feed.adapter.in.web.request.UpdateFeedRequest;
import com.pickong.feed.adapter.in.web.response.FeedPageResponse;
import com.pickong.feed.adapter.in.web.response.FeedPlaceResponse;
import com.pickong.feed.adapter.in.web.response.FeedResponse;
import com.pickong.feed.application.dto.FeedPage;
import com.pickong.feed.domain.FeedErrorCode;

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
    public FeedResponse getFeed(@PathVariable @Positive Long feedId,
            @CurrentUser(required = false) LoginUser viewer, Authentication authentication) {
        boolean admin = authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AdminPrincipal principal && principal.adminId() != null && principal.adminId() > 0
                && authentication.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
        return FeedResponse.from(feedUseCase.getFeed(feedId, viewer == null ? null : viewer.userId(), admin));
    }

    // 피드 목록 조회. tab=recommended(추천) | tab=following(팔로잉). limit은 1~50 사이만 허용
    // cursor: 이전 응답의 nextCursor 값 (첫 요청 시 생략)
    // 로그인 필수 — 비로그인 요청 시 401 반환
    @GetMapping
    public FeedPageResponse getFeeds(
            @RequestParam @NotNull String tab,
            @CurrentUser LoginUser user,
            @RequestParam(required = false) Long cursor,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        // recommended, following 외 tab 값은 400 반환
        if (!tab.equals("recommended") && !tab.equals("following")) {
            throw new BusinessException(FeedErrorCode.INVALID_TAB);
        }
        Long userId = user.userId();
        FeedPage page = tab.equals("following")
                ? feedUseCase.getFollowingFeeds(userId, cursor, limit)
                : feedUseCase.getRecommendedFeeds(userId, cursor, limit);
        return FeedPageResponse.of(
                page.getFeeds().stream().map(FeedResponse::from).toList(),
                page.getNextCursor(),
                page.isHasNext());
    }

    // 피드 지도 장소 목록 조회
    // tab=recommended(추천) | tab=following(팔로잉)
    // 비로그인 시 recommended만 허용, following은 빈 목록 반환
    @GetMapping("/places")
    public List<FeedPlaceResponse> getFeedPlaces(
            @RequestParam @NotNull String tab,
            @CurrentUser(required = false) LoginUser user) {
        Long userId = user != null ? user.userId() : null;
        return feedUseCase.getFeedPlaces(tab, userId).stream()
                .map(FeedPlaceResponse::from)
                .toList();
    }

    // 피드 생성 — 로그인 필수
    @PostMapping
    public FeedResponse createFeed(
            @CurrentUser LoginUser user,
            @Valid @RequestBody CreateFeedRequest request) {
        return FeedResponse.from(feedUseCase.createFeed(request.toCommand(user.userId())));
    }

    // 피드 수정 — 로그인 필수, 본인 피드만 가능
    @PutMapping("/{feedId}")
    public FeedResponse updateFeed(
            @PathVariable @Positive Long feedId,
            @CurrentUser LoginUser user,
            @Valid @RequestBody UpdateFeedRequest request) {
        return FeedResponse.from(feedUseCase.updateFeed(feedId, user.userId(), request.toCommand()));
    }

    // 피드 삭제 — 로그인 필수, 본인 피드만 가능, 204 No Content (댓글 삭제와 같게). 소프트 삭제라 댓글, 반응은 남음
    @DeleteMapping("/{feedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeed(
            @PathVariable @Positive Long feedId,
            @CurrentUser LoginUser user) {
        feedUseCase.deleteFeed(feedId, user.userId());
    }


}
