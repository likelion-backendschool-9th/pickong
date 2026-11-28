package com.pickong.feed.adapter.in.web;

import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.common.security.AdminPrincipal;
import org.springframework.security.core.Authentication;
import com.pickong.feed.adapter.in.web.request.CreateCommentRequest;
import com.pickong.feed.adapter.in.web.request.UpdateCommentRequest;
import com.pickong.feed.adapter.in.web.response.CommentPageResponse;
import com.pickong.feed.adapter.in.web.response.CommentResponse;
import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.port.in.CommentUseCase;
import com.pickong.feed.application.port.in.CommentViewUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/feeds/{feedId}/comments")
public class CommentController {

    private final CommentUseCase commentUseCase;
    // 응답 직전에 작성자 요약을 붙임 (공개 범위 · 숨김 검사는 commentUseCase가 먼저 끝냄)
    private final CommentViewUseCase commentViewUseCase;

    public CommentController(CommentUseCase commentUseCase, CommentViewUseCase commentViewUseCase) {
        this.commentUseCase = commentUseCase;
        this.commentViewUseCase = commentViewUseCase;
    }

    // 댓글 목록 조회 (최상위 댓글만) — 로그인 필수
    @GetMapping
    public CommentPageResponse getComments(
            @PathVariable @Positive Long feedId,
            @RequestParam(required = false) Long cursor,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit,
            @CurrentUser(required = false) LoginUser viewer, Authentication authentication) {
        CommentPage page = commentUseCase.getComments(feedId, cursor, limit, viewer == null ? null : viewer.userId(), isAdmin(authentication));
        return CommentPageResponse.of(
                commentViewUseCase.viewsOf(page.getComments()).stream().map(CommentResponse::from).toList(),
                page.getNextCursor(),
                page.isHasNext()
        );
    }

    // 대댓글 목록 조회 — 로그인 필수
    @GetMapping("/{commentId}/replies")
    public CommentPageResponse getReplies(
            @PathVariable @Positive Long feedId,
            @PathVariable @Positive Long commentId,
            @RequestParam(required = false) Long cursor,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit,
            @CurrentUser(required = false) LoginUser viewer, Authentication authentication) {
        CommentPage page = commentUseCase.getReplies(feedId, commentId, cursor, limit, viewer == null ? null : viewer.userId(), isAdmin(authentication));
        return CommentPageResponse.of(
                commentViewUseCase.viewsOf(page.getComments()).stream().map(CommentResponse::from).toList(),
                page.getNextCursor(),
                page.isHasNext()
        );
    }

    // 댓글 작성 (parentId 있으면 대댓글) — 로그인 필수, 생성 성공 시 201 Created
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable @Positive Long feedId,
            @CurrentUser LoginUser user,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = CommentResponse.from(
                commentViewUseCase.viewOf(commentUseCase.createComment(feedId, request.toCommand(user.userId()))));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 댓글 수정 — 로그인 필수, 본인 댓글만 가능
    @PutMapping("/{commentId}")
    public CommentResponse updateComment(
            @PathVariable @Positive Long feedId,
            @PathVariable @Positive Long commentId,
            @CurrentUser LoginUser user,
            @Valid @RequestBody UpdateCommentRequest request) {
        return CommentResponse.from(
                commentViewUseCase.viewOf(commentUseCase.updateComment(feedId, commentId, user.userId(), request.toCommand())));
    }

    // 댓글 삭제 — 로그인 필수, 본인 댓글만 가능, 삭제 성공 시 204 No Content
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable @Positive Long feedId,
            @PathVariable @Positive Long commentId,
            @CurrentUser LoginUser user) {
        commentUseCase.deleteComment(feedId, commentId, user.userId());
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AdminPrincipal principal && principal.adminId() != null && principal.adminId() > 0
                && authentication.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
    }
}
