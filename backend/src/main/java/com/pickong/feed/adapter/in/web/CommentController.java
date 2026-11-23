package com.pickong.feed.adapter.in.web;

import com.pickong.feed.adapter.in.web.request.CreateCommentRequest;
import com.pickong.feed.adapter.in.web.request.UpdateCommentRequest;
import com.pickong.feed.adapter.in.web.response.CommentPageResponse;
import com.pickong.feed.adapter.in.web.response.CommentResponse;
import com.pickong.feed.application.dto.CommentPage;
import com.pickong.feed.application.port.in.CommentUseCase;
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

    public CommentController(CommentUseCase commentUseCase) {
        this.commentUseCase = commentUseCase;
    }

    // 댓글 목록 조회 (최상위 댓글만)
    @GetMapping
    public CommentPageResponse getComments(
            @PathVariable @Positive Long feedId,
            @RequestParam(required = false) Long cursor,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        CommentPage page = commentUseCase.getComments(feedId, cursor, limit);
        return CommentPageResponse.of(
                page.getComments().stream().map(CommentResponse::from).toList(),
                page.getNextCursor(),
                page.isHasNext()
        );
    }

    // 대댓글 목록 조회
    @GetMapping("/{commentId}/replies")
    public CommentPageResponse getReplies(
            @PathVariable @Positive Long feedId,
            @PathVariable @Positive Long commentId,
            @RequestParam(required = false) Long cursor,
            @RequestParam @Min(value = 1, message = "limit은 1 이상이어야 합니다") @Max(value = 50, message = "limit은 50 이하이어야 합니다") int limit) {
        CommentPage page = commentUseCase.getReplies(feedId, commentId, cursor, limit);
        return CommentPageResponse.of(
                page.getComments().stream().map(CommentResponse::from).toList(),
                page.getNextCursor(),
                page.isHasNext()
        );
    }

    // 댓글 작성 (parentId 있으면 대댓글) — 생성 성공 시 201 Created
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable @Positive Long feedId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = CommentResponse.from(commentUseCase.createComment(feedId, request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 댓글 수정
    @PutMapping("/{commentId}")
    public CommentResponse updateComment(
            @PathVariable @Positive Long feedId,
            @PathVariable @Positive Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return CommentResponse.from(commentUseCase.updateComment(commentId, request.toCommand()));
    }

    // 댓글 삭제 — 삭제 성공 시 204 No Content
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable @Positive Long feedId,
            @PathVariable @Positive Long commentId) {
        commentUseCase.deleteComment(commentId);
    }
}
