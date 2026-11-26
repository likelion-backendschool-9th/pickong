package com.pickong.feed.adapter.in.web.admin;

import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.CurrentAdmin;
import com.pickong.feed.application.port.in.FeedAdminQueryUseCase;
import com.pickong.feed.application.port.in.FeedModerationUseCase;
import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.error.ErrorResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 게시물 목록. 기존 ROLE_ADMIN 인가와 AdminPrincipal 검증을 그대로 사용합니다. */
@RestController
@RequestMapping("/admin/feeds")
public class AdminFeedController {
    private final FeedAdminQueryUseCase feeds;
    private final FeedModerationUseCase moderation;

    public AdminFeedController(FeedAdminQueryUseCase feeds, FeedModerationUseCase moderation) {
        this.feeds = feeds;
        this.moderation = moderation;
    }

    @GetMapping
    public List<AdminFeedResponse> list(@CurrentAdmin AdminPrincipal admin, @RequestParam(required = false) String q) {
        return feeds.list(q).stream().map(AdminFeedResponse::from).toList();
    }

    @PatchMapping("/{feedId}")
    public AdminFeedResponse setHidden(@CurrentAdmin AdminPrincipal admin, @PathVariable String feedId,
            @RequestBody(required = false) SetFeedHiddenRequest body) {
        if (body == null) throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        UUID id;
        try {
            id = UUID.fromString(feedId);
            if (!id.toString().equalsIgnoreCase(feedId)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return AdminFeedResponse.from(moderation.setHidden(id, body.hiddenValue(), body.reasonValue(), admin.adminId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unreadableBody(HttpMessageNotReadableException exception) {
        return ErrorResponse.of(CommonErrorCode.INVALID_INPUT);
    }
}
